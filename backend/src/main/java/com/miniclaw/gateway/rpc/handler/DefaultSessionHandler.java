package com.miniclaw.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.GatewaySession;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class DefaultSessionHandler implements SessionHandler {

    private final InMemorySessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public DefaultSessionHandler(InMemorySessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> supportedMethods() {
        return List.of("session.create");
    }

    @Override
    public Mono<Object> handle(String connectionId, RpcRequestFrame request) {
        GatewaySession session = sessionRegistry.create(connectionId);
        return Mono.just(RpcCompletedFrame.of(
                request.getRequestId(),
                session.getSessionId(),
                completedPayload(session)
        ));
    }

    private ObjectNode completedPayload(GatewaySession session) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("created", true);
        payload.put("sessionId", session.getSessionId());
        return payload;
    }
}
