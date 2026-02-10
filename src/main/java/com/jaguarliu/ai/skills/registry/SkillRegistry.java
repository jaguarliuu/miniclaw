package com.jaguarliu.ai.skills.registry;

import com.jaguarliu.ai.skills.gating.GatingResult;
import com.jaguarliu.ai.skills.gating.SkillGatingService;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.parser.SkillParseResult;
import com.jaguarliu.ai.skills.parser.SkillParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 注册表
 *
 * 职责：
 * 1. 扫描多级目录（项目级 > 用户级 > 内置）
 * 2. 解析 SKILL.md 文件
 * 3. 评估可用性条件（Gating）
 * 4. 缓存 SkillEntry
 * 5. 提供查询接口
 *
 * 优先级规则：
 * - 项目级 (priority=0) 覆盖 用户级 (priority=1) 覆盖 内置 (priority=2)
 * - 同名 skill 只保留最高优先级的版本
 */
@Slf4j
@Service
public class SkillRegistry {

    private final SkillParser parser;
    private final SkillGatingService gatingService;

    @Value("${skills.project-dir:}")
    private String configProjectDir;

    @Value("${skills.user-dir:}")
    private String configUserDir;

    @Value("${skills.builtin-dir:}")
    private String configBuiltinDir;

    // skill name -> SkillEntry 映射（使用 volatile 引用实现原子切换）
    private volatile Map<String, SkillEntry> registry = new ConcurrentHashMap<>();

    // skill name -> 完整正文缓存（loadSkill 时预加载，避免重复文件读取）
    private volatile Map<String, String> bodyCache = new ConcurrentHashMap<>();

    // 快照版本号（每次刷新递增，用于前端缓存控制）
    private volatile long snapshotVersion = 0;

    // 扫描目录配置
    private Path projectSkillsDir;
    private Path userSkillsDir;
    private Path builtinSkillsDir;

    public SkillRegistry(SkillParser parser, SkillGatingService gatingService) {
        this.parser = parser;
        this.gatingService = gatingService;
    }

    /**
     * 初始化时扫描所有 skill 目录
     */
    @PostConstruct
    public void init() {
        // 使用配置值（非空时），否则使用默认目录
        projectSkillsDir = configProjectDir != null && !configProjectDir.isBlank()
                ? Paths.get(configProjectDir)
                : Paths.get(System.getProperty("user.dir"), ".miniclaw", "skills");
        userSkillsDir = configUserDir != null && !configUserDir.isBlank()
                ? Paths.get(configUserDir)
                : Paths.get(System.getProperty("user.home"), ".miniclaw", "skills");
        builtinSkillsDir = configBuiltinDir != null && !configBuiltinDir.isBlank()
                ? Paths.get(configBuiltinDir)
                : Paths.get(System.getProperty("user.dir"), "skills");

        refresh();
    }

    public Path getProjectSkillsDir() { return projectSkillsDir; }
    public Path getUserSkillsDir() { return userSkillsDir; }
    public Path getBuiltinSkillsDir() { return builtinSkillsDir; }

    /**
     * 配置扫描目录（用于测试）
     */
    public void configure(Path projectDir, Path userDir, Path builtinDir) {
        this.projectSkillsDir = projectDir;
        this.userSkillsDir = userDir;
        this.builtinSkillsDir = builtinDir;
    }

    /**
     * 刷新注册表（重新扫描所有目录）
     * 使用 Copy-on-write 模式保证原子性，避免并发读取到半成品状态
     */
    public void refresh() {
        // 构建新的 Map（Copy-on-write）
        Map<String, SkillEntry> newRegistry = new ConcurrentHashMap<>();
        Map<String, String> newBodyCache = new ConcurrentHashMap<>();

        // 按优先级从低到高扫描，高优先级覆盖低优先级
        scanDirectory(builtinSkillsDir, 2, newRegistry, newBodyCache);  // 内置
        scanDirectory(userSkillsDir, 1, newRegistry, newBodyCache);     // 用户级
        scanDirectory(projectSkillsDir, 0, newRegistry, newBodyCache);  // 项目级

        // 原子切换引用（对并发读者可见）
        this.registry = newRegistry;
        this.bodyCache = newBodyCache;
        this.snapshotVersion++;

        log.info("Skill registry refreshed (v{}): {} skills loaded ({} available)",
                snapshotVersion,
                newRegistry.size(),
                newRegistry.values().stream().filter(SkillEntry::isAvailable).count());
    }

    /**
     * 扫描单个目录
     */
    private void scanDirectory(Path dir, int priority, Map<String, SkillEntry> targetRegistry,
                                Map<String, String> targetBodyCache) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir, 2)) { // 最多深入 2 层
            paths.filter(this::isSkillFile)
                    .forEach(path -> loadSkill(path, priority, targetRegistry, targetBodyCache));
        } catch (IOException e) {
            log.warn("Failed to scan skill directory: {}", dir, e);
        }
    }

    /**
     * 判断是否为 SKILL.md 文件
     * 支持两种格式：
     * - skills/<name>/SKILL.md
     * - skills/<name>.SKILL.md
     */
    private boolean isSkillFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.equals("SKILL.md") || fileName.endsWith(".SKILL.md");
    }

    /**
     * 加载单个 skill
     */
    private void loadSkill(Path path, int priority, Map<String, SkillEntry> targetRegistry,
                           Map<String, String> targetBodyCache) {
        try {
            String content = Files.readString(path);
            long lastModified = Files.getLastModifiedTime(path).toMillis();

            SkillParseResult result = parser.parse(content, path, priority, lastModified);

            if (!result.isValid()) {
                log.warn("Failed to parse skill {}: {}", path, result.getErrorMessage());
                return;
            }

            SkillMetadata metadata = result.getMetadata();
            String name = metadata.getName();

            // 检查优先级：只有更高优先级（数字更小）才覆盖
            SkillEntry existing = targetRegistry.get(name);
            if (existing != null && existing.getMetadata().getPriority() <= priority) {
                log.debug("Skill '{}' already registered with higher priority, skipping", name);
                return;
            }

            // 执行 Gating 检查
            GatingResult gatingResult = gatingService.evaluate(metadata.getRequires());

            // 构建 SkillEntry
            SkillEntry entry = SkillEntry.builder()
                    .metadata(metadata)
                    .available(gatingResult.isAvailable())
                    .unavailableReason(gatingResult.getFailureReason())
                    .lastModified(lastModified)
                    .tokenCost(SkillEntry.calculateTokenCost(metadata))
                    .build();

            targetRegistry.put(name, entry);

            // 预加载正文到缓存（避免重复文件读取）
            targetBodyCache.put(name, result.getBody());

            log.debug("Loaded skill '{}' from {} (available={})",
                    name, path, gatingResult.isAvailable());

        } catch (IOException e) {
            log.warn("Failed to read skill file: {}", path, e);
        }
    }

    /**
     * 获取所有已注册的 skill
     */
    public List<SkillEntry> getAll() {
        return new ArrayList<>(registry.values());
    }

    /**
     * 获取所有可用的 skill
     */
    public List<SkillEntry> getAvailable() {
        return registry.values().stream()
                .filter(SkillEntry::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有不可用的 skill（用于诊断）
     */
    public List<SkillEntry> getUnavailable() {
        return registry.values().stream()
                .filter(e -> !e.isAvailable())
                .collect(Collectors.toList());
    }

    /**
     * 按名称查找 skill
     */
    public Optional<SkillEntry> getByName(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    /**
     * 检查 skill 是否存在且可用
     */
    public boolean isAvailable(String name) {
        SkillEntry entry = registry.get(name);
        return entry != null && entry.isAvailable();
    }

    /**
     * 激活 skill（加载完整内容）
     *
     * @param name skill 名称
     * @return 加载后的完整 skill，如果不存在或不可用返回 empty
     */
    public Optional<LoadedSkill> activate(String name) {
        SkillEntry entry = registry.get(name);
        if (entry == null) {
            log.warn("Skill not found: {}", name);
            return Optional.empty();
        }

        if (!entry.isAvailable()) {
            log.warn("Skill not available: {} (reason: {})", name, entry.getUnavailableReason());
            return Optional.empty();
        }

        String body = bodyCache.get(name);
        if (body == null) {
            // 如果缓存丢失，尝试重新读取
            body = reloadBody(entry.getMetadata().getSourcePath());
            if (body == null) {
                return Optional.empty();
            }
            bodyCache.put(name, body);
        }

        SkillMetadata meta = entry.getMetadata();

        // 获取 skill 的基础目录
        Path basePath = null;
        if (meta.getSourcePath() != null) {
            basePath = meta.getSourcePath().getParent();
        }

        LoadedSkill loaded = LoadedSkill.builder()
                .name(meta.getName())
                .description(meta.getDescription())
                .body(body)
                .basePath(basePath)
                .allowedTools(meta.getAllowedTools() != null ?
                        new HashSet<>(meta.getAllowedTools()) : null)
                .confirmBefore(meta.getConfirmBefore() != null ?
                        new HashSet<>(meta.getConfirmBefore()) : null)
                .build();

        log.info("Activated skill: {} (basePath={})", name, basePath);
        return Optional.of(loaded);
    }

    /**
     * 重新读取 skill 正文
     */
    private String reloadBody(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            String content = Files.readString(path);
            SkillParseResult result = parser.parse(content, path, 0, System.currentTimeMillis());
            return result.isValid() ? result.getBody() : null;
        } catch (IOException e) {
            log.warn("Failed to reload skill body: {}", path, e);
            return null;
        }
    }

    /**
     * 检查是否有 skill 文件变化（修改/新增/删除）
     * 使用轻量级目录扫描，不进行完整解析
     */
    public boolean hasChanges() {
        // 构建当前文件系统的快照 (path -> lastModified)
        Map<Path, Long> currentSnapshot = new HashMap<>();
        collectSkillFiles(builtinSkillsDir, currentSnapshot);
        collectSkillFiles(userSkillsDir, currentSnapshot);
        collectSkillFiles(projectSkillsDir, currentSnapshot);

        // 构建已注册 skill 的快照
        Map<Path, Long> registeredSnapshot = new HashMap<>();
        for (SkillEntry entry : registry.values()) {
            Path path = entry.getMetadata().getSourcePath();
            if (path != null) {
                registeredSnapshot.put(path, entry.getLastModified());
            }
        }

        // 检查新增文件（在 currentSnapshot 但不在 registeredSnapshot）
        for (Path path : currentSnapshot.keySet()) {
            if (!registeredSnapshot.containsKey(path)) {
                log.debug("Detected new skill file: {}", path);
                return true;
            }
        }

        // 检查删除文件（在 registeredSnapshot 但不在 currentSnapshot）
        for (Path path : registeredSnapshot.keySet()) {
            if (!currentSnapshot.containsKey(path)) {
                log.debug("Detected deleted skill file: {}", path);
                return true;
            }
        }

        // 检查修改文件（lastModified 不同）
        for (Map.Entry<Path, Long> entry : currentSnapshot.entrySet()) {
            Path path = entry.getKey();
            Long currentModified = entry.getValue();
            Long registeredModified = registeredSnapshot.get(path);

            if (registeredModified != null && currentModified > registeredModified) {
                log.debug("Detected modified skill file: {}", path);
                return true;
            }
        }

        return false;
    }

    /**
     * 收集指定目录下的所有 skill 文件及其修改时间
     */
    private void collectSkillFiles(Path dir, Map<Path, Long> snapshot) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir, 2)) {
            paths.filter(this::isSkillFile)
                    .forEach(path -> {
                        try {
                            long lastModified = Files.getLastModifiedTime(path).toMillis();
                            snapshot.put(path, lastModified);
                        } catch (IOException e) {
                            // 忽略无法读取的文件
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to collect skill files from: {}", dir, e);
        }
    }

    /**
     * 获取注册表统计信息
     */
    public RegistryStats getStats() {
        int total = registry.size();
        int available = (int) registry.values().stream().filter(SkillEntry::isAvailable).count();
        int totalTokens = registry.values().stream()
                .filter(SkillEntry::isAvailable)
                .mapToInt(SkillEntry::getTokenCost)
                .sum();

        return new RegistryStats(total, available, total - available, totalTokens);
    }

    /**
     * 注册表统计
     */
    public record RegistryStats(
            int totalSkills,
            int availableSkills,
            int unavailableSkills,
            int totalTokenCost
    ) {}

    /**
     * 获取已注册 skill 数量
     */
    public int size() {
        return registry.size();
    }

    /**
     * 获取快照版本号
     */
    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    /**
     * 清空注册表（用于测试）
     */
    public void clear() {
        registry.clear();
        bodyCache.clear();
    }
}
