package com.miniclaw.gateway.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
    }

    @Test
    void shouldRegisterConnectionWithGeneratedId() {
        ConnectionContext connection = registry.register(mock(WebSocketSession.class));

        assertFalse(connection.getConnectionId().isBlank());
        assertEquals(1, registry.size());
        assertTrue(registry.find(connection.getConnectionId()).isPresent());
    }

    @Test
    void shouldBindMultipleBusinessSessionsToSingleConnection() {
        ConnectionContext connection = registry.register(mock(WebSocketSession.class));

        registry.bindSession(connection.getConnectionId(), "session-a");
        registry.bindSession(connection.getConnectionId(), "session-b");

        assertTrue(registry.findBySessionId("session-a").isPresent());
        assertTrue(registry.findBySessionId("session-b").isPresent());
        assertEquals(connection.getConnectionId(),
                registry.findBySessionId("session-a").orElseThrow().getConnectionId());
        assertEquals(2, registry.find(connection.getConnectionId()).orElseThrow().getSessionIds().size());
    }

    @Test
    void shouldRemoveSessionBindingsWhenConnectionCloses() {
        ConnectionContext connection = registry.register(mock(WebSocketSession.class));
        registry.bindSession(connection.getConnectionId(), "session-a");
        registry.bindSession(connection.getConnectionId(), "session-b");

        registry.remove(connection.getConnectionId());

        assertEquals(0, registry.size());
        assertTrue(registry.find(connection.getConnectionId()).isEmpty());
        assertTrue(registry.findBySessionId("session-a").isEmpty());
        assertTrue(registry.findBySessionId("session-b").isEmpty());
    }
}
