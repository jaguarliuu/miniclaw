package com.jaguarliu.ai.tools.builtin.process;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 被管理的进程
 * 包含进程引用、输出缓冲区、状态等
 */
@Slf4j
@Data
public class ManagedProcess {

    private final String id;
    private final String command;
    private final Process process;
    private final Instant startTime;

    /**
     * 输出缓冲区
     */
    private final StringBuilder outputBuffer = new StringBuilder();

    /**
     * 已读取的输出偏移量（用于返回增量输出）
     */
    private final AtomicInteger readOffset = new AtomicInteger(0);

    /**
     * 进程结束时间（用于自动清理）
     */
    private Instant endTime;

    /**
     * 退出码（进程结束后才有值）
     */
    private Integer exitCode;

    /**
     * 后台读取线程
     */
    private Thread readerThread;

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    public ManagedProcess(String id, String command, Process process) {
        this.id = id;
        this.command = command;
        this.process = process;
        this.startTime = Instant.now();
    }

    /**
     * 启动后台输出读取线程
     */
    public void startOutputReader() {
        readerThread = new Thread(() -> {
            Charset charset = IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (outputBuffer) {
                        if (outputBuffer.length() > 0) {
                            outputBuffer.append("\n");
                        }
                        outputBuffer.append(line);
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading process output: {}", e.getMessage());
            }

            // 进程结束
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            endTime = Instant.now();
            log.info("Process {} finished with exit code {}", id, exitCode);
        }, "process-reader-" + id);

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * 进程是否仍在运行
     */
    public boolean isRunning() {
        return process.isAlive();
    }

    /**
     * 获取全部输出
     */
    public String getAllOutput() {
        synchronized (outputBuffer) {
            return outputBuffer.toString();
        }
    }

    /**
     * 获取增量输出（从上次读取位置开始）
     */
    public String getIncrementalOutput() {
        synchronized (outputBuffer) {
            int currentLength = outputBuffer.length();
            int offset = readOffset.get();

            if (offset >= currentLength) {
                return "";
            }

            String incremental = outputBuffer.substring(offset, currentLength);
            readOffset.set(currentLength);
            return incremental;
        }
    }

    /**
     * 终止进程
     */
    public void kill() {
        if (process.isAlive()) {
            process.destroyForcibly();
            log.info("Process {} killed", id);
        }
    }
}
