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
import java.util.Optional;

/**
 * 终止异步进程工具
 * 返回最终输出
 * 不需要 HITL 确认（终止是安全操作）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellKillTool implements Tool {

    private final ProcessManager processManager;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("shell_kill")
                .description("终止一个正在运行的异步进程。返回进程的最终输出。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "processId", Map.of(
                                        "type", "string",
                                        "description", "进程 ID（由 shell_start 返回）"
                                )
                        ),
                        "required", List.of("processId")
                ))
                .hitl(false)  // 终止进程是安全操作，不需要确认
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String processId = (String) arguments.get("processId");
            if (processId == null || processId.isBlank()) {
                return ToolResult.error("Missing required parameter: processId");
            }

            Optional<ManagedProcess> mpOpt = processManager.getProcess(processId);
            if (mpOpt.isEmpty()) {
                return ToolResult.error("Process not found: " + processId);
            }

            ManagedProcess mp = mpOpt.get();
            boolean wasRunning = mp.isRunning();

            if (wasRunning) {
                mp.kill();
                // 等待一小段时间让进程终止
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            String allOutput = mp.getAllOutput();

            StringBuilder result = new StringBuilder();
            result.append("Process ID: ").append(processId).append("\n");
            result.append("Command: ").append(mp.getCommand()).append("\n");

            if (wasRunning) {
                result.append("Action: Process terminated\n");
            } else {
                result.append("Action: Process was already finished\n");
                result.append("Exit code: ").append(mp.getExitCode()).append("\n");
            }

            result.append("\n--- Final output ---\n");
            if (allOutput.isEmpty()) {
                result.append("(no output)\n");
            } else {
                result.append(allOutput).append("\n");
            }

            return ToolResult.success(result.toString());
        });
    }
}
