package com.jaguarliu.ai.tools.builtin.process;

import com.jaguarliu.ai.tools.ToolsProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 进程管理器
 * 管理所有异步启动的进程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessManager {

    private final ToolsProperties properties;

    /**
     * 存储所有被管理的进程
     */
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

    /**
     * 清理调度器
     */
    private ScheduledExecutorService cleanupScheduler;

    /**
     * 进程结束后保留输出的时间（秒）
     */
    private static final long RETAIN_SECONDS = 300; // 5 分钟

    /**
     * 清理间隔（秒）
     */
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    @PostConstruct
    public void init() {
        // 启动定期清理任务
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "process-cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredProcesses,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("ProcessManager initialized");
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down ProcessManager...");

        // 终止所有运行中的进程
        processes.values().forEach(mp -> {
            if (mp.isRunning()) {
                mp.kill();
            }
        });
        processes.clear();

        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
    }

    /**
     * 启动一个新进程
     *
     * @param command 要执行的命令
     * @return 管理的进程对象
     */
    public ManagedProcess startProcess(String command) throws Exception {
        String processId = UUID.randomUUID().toString().substring(0, 8);

        // 构建进程
        ProcessBuilder pb = buildProcess(command);

        // 工作目录设为 workspace
        Path workspacePath = Path.of(properties.getWorkspace()).toAbsolutePath().normalize();
        pb.directory(workspacePath.toFile());

        // 合并 stdout 和 stderr
        pb.redirectErrorStream(true);

        Process process = pb.start();

        ManagedProcess mp = new ManagedProcess(processId, command, process);
        mp.startOutputReader();

        processes.put(processId, mp);

        log.info("Started process {}: {}", processId, command);

        return mp;
    }

    /**
     * 获取进程
     */
    public Optional<ManagedProcess> getProcess(String processId) {
        return Optional.ofNullable(processes.get(processId));
    }

    /**
     * 终止进程
     */
    public boolean killProcess(String processId) {
        ManagedProcess mp = processes.get(processId);
        if (mp == null) {
            return false;
        }

        mp.kill();
        return true;
    }

    /**
     * 获取所有进程 ID
     */
    public java.util.Set<String> getAllProcessIds() {
        return processes.keySet();
    }

    /**
     * 清理过期的进程记录
     */
    private void cleanupExpiredProcesses() {
        Instant now = Instant.now();
        int removed = 0;

        var iterator = processes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ManagedProcess mp = entry.getValue();

            // 只清理已结束的进程
            if (!mp.isRunning() && mp.getEndTime() != null) {
                Duration elapsed = Duration.between(mp.getEndTime(), now);
                if (elapsed.getSeconds() > RETAIN_SECONDS) {
                    iterator.remove();
                    removed++;
                    log.debug("Cleaned up expired process: {}", entry.getKey());
                }
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired process records", removed);
        }
    }

    /**
     * 根据操作系统构建进程
     */
    private ProcessBuilder buildProcess(String command) {
        if (IS_WINDOWS) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("/bin/sh", "-c", command);
        }
    }
}
