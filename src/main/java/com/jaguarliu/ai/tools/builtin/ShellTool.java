package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Shell 命令执行工具
 * 默认不需要 HITL 确认，但危险命令会触发确认
 * 支持 Windows 和 Linux/Mac
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellTool implements Tool {

    private final ToolsProperties properties;

    /**
     * 命令执行超时（秒）
     */
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * 最大输出长度
     */
    private static final int MAX_OUTPUT_LENGTH = 32000;

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    /**
     * 用于异步执行的线程池
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public ToolDefinition getDefinition() {
        String osHint = IS_WINDOWS ? "当前为 Windows 环境，请使用 Windows 命令。" : "当前为 Linux/Mac 环境。";
        return ToolDefinition.builder()
                .name("shell")
                .description("执行 shell 命令并返回输出。工作目录为 workspace。超时 " + TIMEOUT_SECONDS + " 秒。" + osHint)
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of(
                                        "type", "string",
                                        "description", "要执行的命令"
                                )
                        ),
                        "required", List.of("command")
                ))
                .hitl(false)  // 默认不需要确认，危险命令由 DangerousCommandDetector 检测
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String command = (String) arguments.get("command");
            if (command == null || command.isBlank()) {
                return ToolResult.error("Missing required parameter: command");
            }

            log.info("Executing shell command: {}", command);

            Process process = null;
            try {
                // 构建进程
                ProcessBuilder pb = buildProcess(command);

                // 工作目录设为 session workspace
                Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
                pb.directory(workspacePath.toFile());

                // 合并 stdout 和 stderr
                pb.redirectErrorStream(true);

                process = pb.start();
                final Process finalProcess = process;

                // 异步读取输出
                Future<String> outputFuture = executor.submit(() -> readOutput(finalProcess));

                // 等待结果，带超时
                String output;
                try {
                    output = outputFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // 超时，强制终止进程
                    finalProcess.destroyForcibly();
                    outputFuture.cancel(true);
                    log.warn("Shell command timed out: {}", command);
                    return ToolResult.error("Command timed out after " + TIMEOUT_SECONDS + " seconds. The process has been terminated.");
                }

                int exitCode = finalProcess.waitFor();

                log.info("Shell command completed: exitCode={}, outputLength={}",
                        exitCode, output.length());

                if (exitCode == 0) {
                    return ToolResult.success(output.isEmpty() ? "(no output)" : output);
                } else {
                    return ToolResult.error("Exit code: " + exitCode + "\n" + output);
                }

            } catch (Exception e) {
                log.error("Shell command failed: {}", command, e);
                if (process != null) {
                    process.destroyForcibly();
                }
                return ToolResult.error("Command execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * 读取进程输出
     */
    private String readOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        Charset charset = IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(line);

                // 输出过长时截断
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output.append("\n\n[Truncated: output exceeds ").append(MAX_OUTPUT_LENGTH).append(" chars]");
                    break;
                }
            }
        }

        return output.toString();
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
