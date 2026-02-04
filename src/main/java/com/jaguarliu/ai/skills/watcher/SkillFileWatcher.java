package com.jaguarliu.ai.skills.watcher;

import com.jaguarliu.ai.skills.registry.SkillRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Skill 文件监听器
 *
 * 实时监听 skill 目录变化，触发 SkillRegistry 更新
 * 变更在下一个 turn 或新 session 生效
 *
 * 监听事件：
 * - SKILL.md 创建/修改 → registry.refresh() 或增量更新
 * - SKILL.md 删除 → 从 registry 移除
 * - 新建 skill 目录 → 注册监听 + 检查 SKILL.md
 */
@Slf4j
@Component
public class SkillFileWatcher {

    private final SkillRegistry registry;

    @Value("${skills.watch-enabled:true}")
    private boolean watchEnabled;

    private WatchService watchService;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // WatchKey → 监听的目录信息
    private final Map<WatchKey, WatchedPath> watchKeyMap = new HashMap<>();

    public SkillFileWatcher(SkillRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void start() {
        if (!watchEnabled) {
            log.info("Skill file watcher is disabled");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "skill-watcher");
                t.setDaemon(true);
                return t;
            });

            // 注册监听目录
            registerWatchPaths();

            running.set(true);
            executor.submit(this::watchLoop);

            log.info("Skill file watcher started");
        } catch (IOException e) {
            log.error("Failed to start skill file watcher", e);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }

        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Skill file watcher stopped");
    }

    /**
     * 注册需要监听的目录
     */
    private void registerWatchPaths() {
        // 项目级
        Path projectSkills = Paths.get(System.getProperty("user.dir"), ".miniclaw", "skills");
        registerDirectory(projectSkills, 0);

        // 用户级
        Path userSkills = Paths.get(System.getProperty("user.home"), ".miniclaw", "skills");
        registerDirectory(userSkills, 1);

        // 内置
        Path builtinSkills = Paths.get(System.getProperty("user.dir"), "skills");
        registerDirectory(builtinSkills, 2);
    }

    /**
     * 注册单个目录及其子目录
     */
    private void registerDirectory(Path dir, int priority) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.debug("Skill directory does not exist: {}", dir);
            return;
        }

        try {
            // 监听根目录（新增/删除 skill 文件夹）
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeyMap.put(key, new WatchedPath(dir, priority, true));
            log.info("Watching skill directory: {}", dir);

            // 监听每个 skill 子目录
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).forEach(subDir -> {
                    registerSubDirectory(subDir, priority);
                });
            }
        } catch (IOException e) {
            log.warn("Failed to register watch for: {}", dir, e);
        }
    }

    /**
     * 注册 skill 子目录（监听 SKILL.md 变化）
     */
    private void registerSubDirectory(Path subDir, int priority) {
        try {
            WatchKey subKey = subDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeyMap.put(subKey, new WatchedPath(subDir, priority, false));
            log.debug("Watching skill subdirectory: {}", subDir);
        } catch (IOException e) {
            log.warn("Failed to watch subdirectory: {}", subDir, e);
        }
    }

    /**
     * 监听循环
     */
    private void watchLoop() {
        log.debug("Watch loop started");

        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                WatchedPath watchedPath = watchKeyMap.get(key);
                if (watchedPath == null) {
                    key.reset();
                    continue;
                }

                // 防抖：收集短时间内的所有事件
                Thread.sleep(100);

                boolean needsRefresh = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        // 事件溢出，需要完整刷新
                        needsRefresh = true;
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedPath = watchedPath.path.resolve(pathEvent.context());

                    needsRefresh |= handleChange(kind, changedPath, watchedPath);
                }

                if (needsRefresh) {
                    log.info("Refreshing skill registry due to file changes");
                    registry.refresh();
                }

                // 重置 key，继续监听
                boolean valid = key.reset();
                if (!valid) {
                    watchKeyMap.remove(key);
                    log.debug("Watch key invalidated for: {}", watchedPath.path);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                log.error("Error in watch loop", e);
            }
        }

        log.debug("Watch loop ended");
    }

    /**
     * 处理文件变化事件
     *
     * @return 是否需要刷新 registry
     */
    private boolean handleChange(WatchEvent.Kind<?> kind, Path changedPath, WatchedPath watchedPath) {
        String fileName = changedPath.getFileName().toString();

        // 1. 根目录下新建了文件夹（新 skill）
        if (watchedPath.isRoot && kind == StandardWatchEventKinds.ENTRY_CREATE) {
            if (Files.isDirectory(changedPath)) {
                log.info("New skill directory detected: {}", changedPath);

                // 注册新目录的监听
                registerSubDirectory(changedPath, watchedPath.priority);

                // 检查是否有 SKILL.md
                Path skillFile = changedPath.resolve("SKILL.md");
                if (Files.exists(skillFile)) {
                    return true; // 需要刷新
                }
            }
            // 检查 .SKILL.md 格式
            else if (fileName.endsWith(".SKILL.md")) {
                log.info("New skill file detected: {}", changedPath);
                return true;
            }
            return false;
        }

        // 2. SKILL.md 文件变化
        if ("SKILL.md".equals(fileName) || fileName.endsWith(".SKILL.md")) {
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                log.info("Skill file deleted: {}", changedPath);
            } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                log.info("Skill file created: {}", changedPath);
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                log.info("Skill file modified: {}", changedPath);
            }
            return true;
        }

        // 3. skill 目录被删除
        if (watchedPath.isRoot && kind == StandardWatchEventKinds.ENTRY_DELETE) {
            log.info("Skill directory deleted: {}", changedPath);
            return true;
        }

        return false;
    }

    /**
     * 检查监听器是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取监听的目录数量
     */
    public int getWatchedPathCount() {
        return watchKeyMap.size();
    }

    /**
     * 监听的路径信息
     */
    private record WatchedPath(Path path, int priority, boolean isRoot) {}
}
