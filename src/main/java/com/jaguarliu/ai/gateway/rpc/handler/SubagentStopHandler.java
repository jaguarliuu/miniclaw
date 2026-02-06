package com.jaguarliu.ai.gateway.rpc.handler;

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
 * subagent.stop 处理器
 * 停止指定的子代理运行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubagentStopHandler implements RpcHandler {

    private final SubagentOpsService subagentOpsService;

    @Override
    public String getMethod() {
        return "subagent.stop";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String subRunId = extractString(request.getPayload(), "subRunId");

        if (subRunId == null || subRunId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing subRunId"));
        }

        SubagentOpsService.StopResult result = subagentOpsService.stop(subRunId);

        if (result.success()) {
            log.info("Subagent stop requested: subRunId={}, connectionId={}", subRunId, connectionId);
            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "subRunId", subRunId,
                    "stopped", true,
                    "message", "Stop requested"
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
