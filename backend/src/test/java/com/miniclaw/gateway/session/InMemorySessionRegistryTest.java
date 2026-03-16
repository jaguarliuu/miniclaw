package com.miniclaw.gateway.session;

import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InMemorySessionRegistryTest {

    private ConnectionRegistry connectionRegistry;
    private InMemorySessionRegistry sessionRegistry;

    @BeforeEach
    void setUp() {
        connectionRegistry = new ConnectionRegistry();
        sessionRegistry = new InMemorySessionRegistry(connectionRegistry);
    }

    @Test
    void shouldCreateSessionForExistingConnection() {
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));

        GatewaySession session = sessionRegistry.create(connection.getConnectionId());

        assertFalse(session.getSessionId().isBlank());
        assertEquals(connection.getConnectionId(), session.getConnectionId());
        assertTrue(sessionRegistry.find(session.getSessionId()).isPresent());
        assertEquals(1, sessionRegistry.size());
        assertTrue(connectionRegistry.find(connection.getConnectionId())
                .orElseThrow()
                .getSessionIds()
                .contains(session.getSessionId()));
    }

    @Test
    void shouldRejectSessionCreationForUnknownConnection() {
        assertThrows(IllegalArgumentException.class, () -> sessionRegistry.create("missing-connection"));
    }

    @Test
    void shouldRemoveSessionsByConnectionAndUnbindThem() {
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        GatewaySession first = sessionRegistry.create(connection.getConnectionId());
        GatewaySession second = sessionRegistry.create(connection.getConnectionId());

        sessionRegistry.removeAllByConnection(connection.getConnectionId());

        assertEquals(0, sessionRegistry.size());
        assertTrue(sessionRegistry.find(first.getSessionId()).isEmpty());
        assertTrue(sessionRegistry.find(second.getSessionId()).isEmpty());
        assertTrue(connectionRegistry.find(connection.getConnectionId()).orElseThrow().getSessionIds().isEmpty());
    }
}
