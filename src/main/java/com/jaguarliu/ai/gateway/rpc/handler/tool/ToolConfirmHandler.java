package com.jaguarliu.ai.gateway.rpc.handler.tool;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.runtime.HitlDecision;
import com.jaguarliu.ai.runtime.HitlManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * tool.confirm 处理器
 * 接收用户对 HITL 工具调用的确认/拒绝决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolConfirmHandler implements RpcHandler {

    private final HitlManager hitlManager;

    @Override
    public String getMethod() {
        return "tool.confirm";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = extractPayload(request.getPayload());

        String callId = (String) payload.get("callId");
        String decision = (String) payload.get("decision");

        if (callId == null || callId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing callId"));
        }

        if (decision == null || decision.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing decision"));
        }

        // 解析决策
        HitlDecision hitlDecision;
        switch (decision.toLowerCase()) {
            case "approve" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> modifiedArgs = (Map<String, Object>) payload.get("modifiedArguments");
                hitlDecision = modifiedArgs != null
                        ? HitlDecision.approve(modifiedArgs)
                        : HitlDecision.approve();
            }
            case "reject" -> hitlDecision = HitlDecision.reject();
            default -> {
                return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS",
                        "Invalid decision: " + decision + ". Expected: approve or reject"));
            }
        }

        // 提交决策
        boolean success = hitlManager.submitDecision(callId, hitlDecision);

        if (success) {
            log.info("HITL decision accepted: callId={}, decision={}", callId, decision);
            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "callId", callId,
                    "decision", decision,
                    "accepted", true
            )));
        } else {
            log.warn("HITL decision rejected: callId={}, no pending confirmation", callId);
            return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND",
                    "No pending confirmation for callId: " + callId));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return Map.of();
    }
}
