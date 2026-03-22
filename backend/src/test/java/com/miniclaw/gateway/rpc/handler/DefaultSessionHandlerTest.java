package com.miniclaw.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DefaultSessionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateSessionAndReturnCompletedFrame() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry(connectionRegistry);
        DefaultSessionHandler handler = new DefaultSessionHandler(sessionRegistry, objectMapper);

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-session-create")
                .method("session.create")
                .payload(objectMapper.createObjectNode())
                .build();

        RpcCompletedFrame result = (RpcCompletedFrame) handler.handle(connection.getConnectionId(), request)
                .block();

        assertEquals("completed", result.getType());
        assertNotNull(result.getSessionId());
        assertEquals(result.getSessionId(), result.getPayload().get("sessionId").asText());
        assertTrue(sessionRegistry.find(result.getSessionId()).isPresent());
    }
}
