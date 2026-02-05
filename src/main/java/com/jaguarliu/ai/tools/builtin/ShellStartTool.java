package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.builtin.process.ManagedProcess;
import com.jaguarliu.ai.tools.builtin.process.ProcessManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 异步启动 Shell 命令工具
 * 启动命令后立即返回 processId，不等待命令完成
 * 默认不需要 HITL 确认，但危险命令会触发确认
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellStartTool implements Tool {

    private final ProcessManager processManager;

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    @Override
    public ToolDefinition getDefinition() {
        String osHint = IS_WINDOWS ? "当前为 Windows 环境，请使用 Windows 命令。" : "当前为 Linux/Mac 环境。";
        return ToolDefinition.builder()
                .name("shell_start")
                .description("异步启动 shell 命令，立即返回进程 ID。适用于长时间运行的命令（如 docker-compose up、npm install）。" +
                        "启动后使用 shell_status 查看输出，使用 shell_kill 终止进程。" + osHint)
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

            log.info("Starting async shell command: {}", command);

            try {
                ManagedProcess mp = processManager.startProcess(command);

                // 等待一小段时间，获取初始输出
                Thread.sleep(100);

                String initialOutput = mp.getIncrementalOutput();

                return ToolResult.success(String.format(
                        "Process started successfully.\n" +
                                "Process ID: %s\n" +
                                "Command: %s\n" +
                                "Status: %s\n" +
                                "Initial output:\n%s",
                        mp.getId(),
                        command,
                        mp.isRunning() ? "running" : "exited (code: " + mp.getExitCode() + ")",
                        initialOutput.isEmpty() ? "(no output yet)" : initialOutput
                ));

            } catch (Exception e) {
                log.error("Failed to start process: {}", command, e);
                return ToolResult.error("Failed to start process: " + e.getMessage());
            }
        });
    }
}
