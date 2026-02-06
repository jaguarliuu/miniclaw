package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.subagent.SubagentOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * subagent.list 处理器
 * 列出指定父运行或会话的所有子代理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubagentListHandler implements RpcHandler {

    private final SubagentOpsService subagentOpsService;

    @Override
    public String getMethod() {
        return "subagent.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String parentRunId = extractString(request.getPayload(), "parentRunId");
            String sessionId = extractString(request.getPayload(), "sessionId");

            List<RunEntity> subagents;
            if (parentRunId != null && !parentRunId.isBlank()) {
                // 按父运行 ID 查询
                subagents = subagentOpsService.listByParentRun(parentRunId);
            } else if (sessionId != null && !sessionId.isBlank()) {
                // 按请求会话 ID 查询
                subagents = subagentOpsService.listByRequesterSession(sessionId);
            } else {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS",
                        "Either parentRunId or sessionId is required");
            }

            List<Map<String, Object>> subagentDtos = subagents.stream()
                    .map(this::toSubagentDto)
                    .toList();

            return RpcResponse.success(request.getId(), Map.of("subagents", subagentDtos));

        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> toSubagentDto(RunEntity run) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("subRunId", run.getId());
        dto.put("subSessionId", run.getSessionId());
        dto.put("parentRunId", run.getParentRunId());
        dto.put("requesterSessionId", run.getRequesterSessionId());
        dto.put("agentId", run.getAgentId());
        dto.put("status", run.getStatus());
        dto.put("task", truncate(run.getPrompt(), 100));
        dto.put("deliver", run.getDeliver());
        dto.put("createdAt", run.getCreatedAt().toString());
        if (run.getUpdatedAt() != null) {
            dto.put("updatedAt", run.getUpdatedAt().toString());
        }
        return dto;
    }

    private String extractString(Object payload, String key) {
        if (payload instanceof Map) {
            Object value = ((Map<?, ?>) payload).get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }
}
