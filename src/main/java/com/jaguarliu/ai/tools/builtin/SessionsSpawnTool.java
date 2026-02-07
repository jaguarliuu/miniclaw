package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.subagent.SubagentService;
import com.jaguarliu.ai.subagent.model.SubagentSpawnRequest;
import com.jaguarliu.ai.subagent.model.SubagentSpawnResult;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * sessions_spawn 工具
 * 派生子代理执行异步任务
 *
 * 安全约束：
 * - 禁止嵌套派生（subagent 不能再 spawn）
 * - 需要在 main run 中调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionsSpawnTool implements Tool {

    private final SubagentService subagentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("sessions_spawn")
                .description("""
                        派生子代理执行异步任务。子代理在独立会话中运行，不阻塞当前对话。
                        适用于：长耗时任务、可并行任务、需要隔离上下文的任务。
                        完成后结果会自动回传到当前会话。
                        注意：子代理不能再派生子代理（禁止嵌套）。
                        """)
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "task", Map.of(
                                        "type", "string",
                                        "description", "任务描述，子代理将根据此描述执行任务"
                                ),
                                "agentId", Map.of(
                                        "type", "string",
                                        "description", "目标 Agent Profile ID（可选，默认继承父代理）"
                                ),
                                "deliver", Map.of(
                                        "type", "boolean",
                                        "description", "是否实时转发子任务的中间输出到父会话（默认 false）"
                                ),
                                "announce", Map.of(
                                        "type", "boolean",
                                        "description", "任务完成后是否回传结果摘要（默认 true）"
                                ),
                                "timeoutSeconds", Map.of(
                                        "type", "integer",
                                        "description", "超时时间（秒），默认 600"
                                ),
                                "metadata", Map.of(
                                        "type", "object",
                                        "description", "自定义元数据，会随任务传递"
                                )
                        ),
                        "required", List.of("task")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        // 1. 获取执行上下文
        ToolExecutionContext context = ToolExecutionContext.current();
        if (context == null) {
            log.error("ToolExecutionContext is null, cannot execute sessions_spawn");
            return Mono.just(ToolResult.error("Internal error: execution context not available"));
        }

        // 2. 检查是否为 subagent（禁止嵌套）
        if (context.isSubagent()) {
            log.warn("Nested spawn rejected: runId={}, runKind={}", context.getRunId(), context.getRunKind());
            return Mono.just(ToolResult.error(
                    "Nested spawn is not allowed. SubAgent cannot spawn another SubAgent."
            ));
        }

        // 3. 解析参数
        String task = (String) arguments.get("task");
        if (task == null || task.isBlank()) {
            return Mono.just(ToolResult.error("Parameter 'task' is required"));
        }

        String agentId = (String) arguments.get("agentId");
        Boolean deliver = (Boolean) arguments.getOrDefault("deliver", false);
        Boolean announce = (Boolean) arguments.getOrDefault("announce", true);
        Integer timeoutSeconds = arguments.get("timeoutSeconds") != null
                ? ((Number) arguments.get("timeoutSeconds")).intValue()
                : 600;

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) arguments.get("metadata");

        // 4. 构建 spawn 请求
        SubagentSpawnRequest request = SubagentSpawnRequest.builder()
                .task(task)
                .agentId(agentId)
                .deliver(deliver)
                .announce(announce)
                .timeoutSeconds(timeoutSeconds)
                .metadata(metadata)
                .build();

        // 5. 调用 SubagentService
        SubagentSpawnResult result = subagentService.spawn(
                context.getRunId(),           // 当前 run 作为 parent
                context.getSessionId(),       // 当前 session 作为 parent
                context.getAgentId(),         // 当前 agentId
                context.getConnectionId(),    // WebSocket 连接 ID，用于事件推送
                request
        );

        // 6. 返回结果
        if (result.isAccepted()) {
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "accepted", true,
                        "subSessionId", result.getSubSessionId(),
                        "subRunId", result.getSubRunId(),
                        "sessionKey", result.getSessionKey(),
                        "lane", result.getLane(),
                        "message", "SubAgent spawned successfully. Results will be announced when complete."
                ));
                log.info("SubAgent spawned: parentRunId={}, subRunId={}, task={}",
                        context.getRunId(), result.getSubRunId(), truncateTask(task));
                return Mono.just(ToolResult.success(json));
            } catch (Exception e) {
                return Mono.just(ToolResult.success(
                        "SubAgent spawned: subRunId=" + result.getSubRunId()
                ));
            }
        } else {
            log.warn("SubAgent spawn failed: parentRunId={}, error={}", context.getRunId(), result.getError());
            return Mono.just(ToolResult.error("Spawn failed: " + result.getError()));
        }
    }

    private String truncateTask(String task) {
        if (task == null) return "";
        return task.length() > 50 ? task.substring(0, 47) + "..." : task;
    }
}
