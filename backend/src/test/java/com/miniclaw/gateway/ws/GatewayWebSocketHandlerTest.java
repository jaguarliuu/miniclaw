package com.miniclaw.gateway.ws;

import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayWebSocketHandlerTest {

    @Test
    void shouldRegisterOnConnectAndRemoveOnDisconnect() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(registry, new InMemorySessionRegistry(registry));

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(Flux.never());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> registry.size() == 1, "connection should be registered");

        subscription.dispose();

        waitUntil(() -> registry.size() == 0, "connection should be removed");
    }

    @Test
    void shouldIgnoreInboundTextFramesUntilRouterIsReady() {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(registry, new InMemorySessionRegistry(registry));

        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage message = mock(WebSocketMessage.class);

        when(message.getType()).thenReturn(WebSocketMessage.Type.TEXT);
        when(message.getPayloadAsText()).thenReturn("{\"type\":\"request\"}");
        when(session.receive()).thenReturn(Flux.just(message));

        handler.handle(session).block();

        assertEquals(0, registry.size());
    }

    @Test
    void shouldClearBoundSessionsWhenConnectionDisconnects() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        InMemorySessionRegistry sessionRegistry = mock(InMemorySessionRegistry.class);
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(registry, sessionRegistry);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(Flux.never());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> registry.size() == 1, "connection should be registered");

        subscription.dispose();

        waitUntil(() -> registry.size() == 0, "connection should be removed");
        verify(sessionRegistry).removeAllByConnection(anyString());
    }

    private void waitUntil(Condition condition, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(failureMessage);
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }
}
