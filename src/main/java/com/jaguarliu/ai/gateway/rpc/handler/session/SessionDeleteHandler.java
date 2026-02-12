package com.jaguarliu.ai.gateway.rpc.handler.session;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * session.delete 处理器
 * 删除 session 及其关联的 runs 和 messages
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionDeleteHandler implements RpcHandler {

    private final SessionService sessionService;

    @Override
    public String getMethod() {
        return "session.delete";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = extractSessionId(request.getPayload());

            if (sessionId == null || sessionId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing sessionId");
            }

            boolean deleted = sessionService.delete(sessionId);

            if (!deleted) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Session not found: " + sessionId);
            }

            return RpcResponse.success(request.getId(), Map.of(
                    "sessionId", sessionId,
                    "deleted", true
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }
}
