package com.miniclaw.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import com.miniclaw.gateway.session.PersistentSessionService;
import com.miniclaw.gateway.session.persistence.SessionEntity;
import com.miniclaw.gateway.session.persistence.SessionEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class DefaultSessionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateSessionAndReturnCompletedFrame() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        when(repository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DefaultSessionHandler handler = new DefaultSessionHandler(
                new PersistentSessionService(sessionRegistry, repository),
                objectMapper
        );

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
        verify(repository).save(any(SessionEntity.class));
    }
}
