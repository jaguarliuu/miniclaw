package com.jaguarliu.ai.gateway.rpc.handler.subagent;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.subagent.SubagentOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * subagent.send 处理器
 * 向子代理会话发送新消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubagentSendHandler implements RpcHandler {

    private final SubagentOpsService subagentOpsService;

    @Override
    public String getMethod() {
        return "subagent.send";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String subSessionId = extractString(request.getPayload(), "subSessionId");
        String message = extractString(request.getPayload(), "message");

        if (subSessionId == null || subSessionId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing subSessionId"));
        }

        if (message == null || message.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing message"));
        }

        SubagentOpsService.SendResult result = subagentOpsService.send(connectionId, subSessionId, message);

        if (result.success()) {
            log.info("Subagent send queued: subSessionId={}, newRunId={}, connectionId={}",
                    subSessionId, result.newRunId(), connectionId);
            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "newRunId", result.newRunId(),
                    "subSessionId", result.subSessionId(),
                    "queued", true,
                    "message", "Message sent and run queued"
            )));
        } else {
            return Mono.just(RpcResponse.error(request.getId(), result.errorCode(), result.error()));
        }
    }

    private String extractString(Object payload, String key) {
        if (payload instanceof Map) {
            Object value = ((Map<?, ?>) payload).get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}
