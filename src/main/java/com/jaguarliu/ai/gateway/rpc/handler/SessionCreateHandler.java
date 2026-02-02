package com.jaguarliu.ai.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * session.create 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCreateHandler implements RpcHandler {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "session.create";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String name = extractName(request.getPayload());
            SessionEntity session = sessionService.create(name);
            return RpcResponse.success(request.getId(), toSessionDto(session));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractName(Object payload) {
        if (payload instanceof Map) {
            Object name = ((Map<?, ?>) payload).get("name");
            return name != null ? name.toString() : null;
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
