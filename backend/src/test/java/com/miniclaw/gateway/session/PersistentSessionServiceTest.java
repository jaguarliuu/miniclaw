package com.miniclaw.gateway.session;

import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.session.persistence.SessionEntity;
import com.miniclaw.gateway.session.persistence.SessionEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentSessionServiceTest {

    @Test
    void shouldPersistNewSessionWhenCreated() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry runtimeRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        when(repository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersistentSessionService service = new PersistentSessionService(runtimeRegistry, repository);

        GatewaySession session = service.create(connection.getConnectionId());

        assertEquals(SessionState.IDLE, session.getState());
        assertTrue(runtimeRegistry.find(session.getSessionId()).isPresent());
        verify(repository).save(any(SessionEntity.class));
    }

    @Test
    void shouldLoadSessionFromDatabaseWhenNotInRuntimeRegistry() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        InMemorySessionRegistry runtimeRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        Instant createdAt = Instant.parse("2026-03-22T14:30:00Z");

        when(repository.findById("session-001")).thenReturn(Optional.of(
                SessionEntity.builder()
                        .id("session-001")
                        .ownerId(null)
                        .title("Test Session")
                        .status(SessionState.RUNNING)
                        .createdAt(createdAt)
                        .updatedAt(createdAt)
                        .closedAt(null)
                        .build()
        ));

        PersistentSessionService service = new PersistentSessionService(runtimeRegistry, repository);

        GatewaySession session = service.find("session-001").orElseThrow();

        assertEquals("session-001", session.getSessionId());
        assertNull(session.getConnectionId());
        assertEquals(SessionState.RUNNING, session.getState());
        assertEquals(createdAt, session.getCreatedAt());
    }

    @Test
    void shouldPersistStateChange() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry runtimeRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        when(repository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersistentSessionService service = new PersistentSessionService(runtimeRegistry, repository);
        GatewaySession session = service.create(connection.getConnectionId());
        clearInvocations(repository);

        session.setState(SessionState.RUNNING);
        service.save(session);

        verify(repository).save(any(SessionEntity.class));
        assertEquals(SessionState.RUNNING, runtimeRegistry.find(session.getSessionId()).orElseThrow().getState());
    }
}
