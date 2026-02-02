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
 * 查看异步进程状态工具
 * 返回进程状态和增量输出
 * 不需要 HITL 确认（只是查看）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellStatusTool implements Tool {

    private final ProcessManager processManager;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("shell_status")
                .description("查看异步进程的状态和输出。每次调用返回自上次查看以来的新增输出（增量输出）。")
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
                .hitl(false)  // 查看状态不需要确认
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
                return ToolResult.error("Process not found: " + processId +
                        ". The process may have been cleaned up or never existed.");
            }

            ManagedProcess mp = mpOpt.get();
            String incrementalOutput = mp.getIncrementalOutput();

            StringBuilder result = new StringBuilder();
            result.append("Process ID: ").append(processId).append("\n");
            result.append("Command: ").append(mp.getCommand()).append("\n");

            if (mp.isRunning()) {
                result.append("Status: running\n");
            } else {
                result.append("Status: exited\n");
                result.append("Exit code: ").append(mp.getExitCode()).append("\n");
            }

            result.append("\n--- Incremental output ---\n");
            if (incrementalOutput.isEmpty()) {
                result.append("(no new output)\n");
            } else {
                result.append(incrementalOutput).append("\n");
            }

            return ToolResult.success(result.toString());
        });
    }
}
