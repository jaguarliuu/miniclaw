package com.miniclaw.gateway.ws;

import com.miniclaw.gateway.connection.ConnectionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayWebSocketHandlerTest {

    @Test
    void shouldRegisterOnConnectAndRemoveOnDisconnect() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(registry);

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
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(registry);

        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage message = mock(WebSocketMessage.class);

        when(message.getType()).thenReturn(WebSocketMessage.Type.TEXT);
        when(message.getPayloadAsText()).thenReturn("{\"type\":\"request\"}");
        when(session.receive()).thenReturn(Flux.just(message));

        handler.handle(session).block();

        assertEquals(0, registry.size());
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
