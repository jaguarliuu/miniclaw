package com.jaguarliu.ai.gateway.rpc.handler.session;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

/**
 * session.get 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionGetHandler implements RpcHandler {

    private final SessionService sessionService;

    @Override
    public String getMethod() {
        return "session.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = extractSessionId(request.getPayload());
            if (sessionId == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing sessionId");
            }

            Optional<SessionEntity> session = sessionService.get(sessionId);
            if (session.isEmpty()) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Session not found: " + sessionId);
            }

            return RpcResponse.success(request.getId(), toSessionDto(session.get()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private Map<String, Object> toSessionDto(SessionEntity session) {
        return Map.of(
                "id", session.getId(),
                "name", session.getName(),
                "createdAt", session.getCreatedAt().toString(),
                "updatedAt", session.getUpdatedAt().toString()
        );
    }
}
