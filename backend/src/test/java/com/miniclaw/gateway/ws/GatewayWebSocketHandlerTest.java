package com.miniclaw.gateway.ws;

import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayWebSocketHandlerTest {

    @Test
    void shouldRegisterOnConnectAndRemoveOnDisconnect() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                new OutboundDispatcher(new GatewayEventBus(), new ObjectMapper())
        );

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(Flux.never());
        when(session.send(any())).thenReturn(Mono.never());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> registry.size() == 1, "connection should be registered");

        subscription.dispose();

        waitUntil(() -> registry.size() == 0, "connection should be removed");
    }

    @Test
    void shouldIgnoreInboundTextFramesUntilRouterIsReady() {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                new OutboundDispatcher(new GatewayEventBus(), new ObjectMapper())
        );

        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage message = mock(WebSocketMessage.class);

        when(message.getType()).thenReturn(WebSocketMessage.Type.TEXT);
        when(message.getPayloadAsText()).thenReturn("{\"type\":\"request\"}");
        when(session.receive()).thenReturn(Flux.just(message));
        when(session.send(any())).thenReturn(Mono.empty());

        handler.handle(session).block();

        assertEquals(0, registry.size());
    }

    @Test
    void shouldClearBoundSessionsWhenConnectionDisconnects() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        InMemorySessionRegistry sessionRegistry = mock(InMemorySessionRegistry.class);
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                sessionRegistry,
                new OutboundDispatcher(new GatewayEventBus(), new ObjectMapper())
        );

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.receive()).thenReturn(Flux.never());
        when(session.send(any())).thenReturn(Mono.never());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> registry.size() == 1, "connection should be registered");

        subscription.dispose();

        waitUntil(() -> registry.size() == 0, "connection should be removed");
        verify(sessionRegistry).removeAllByConnection(anyString());
    }

    @Test
    void shouldSendOutboundEventsOnlyToCurrentConnection() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        OutboundDispatcher dispatcher = mock(OutboundDispatcher.class);
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                dispatcher
        );

        WebSocketSession session = mock(WebSocketSession.class);
        List<String> sentPayloads = new ArrayList<>();
        when(session.receive()).thenReturn(Flux.never());
        when(dispatcher.outboundJson(anyString())).thenReturn(Flux.just(
                "{\"type\":\"event\",\"requestId\":\"req-001\",\"sessionId\":\"session-001\",\"name\":\"chat.delta\"}"
        ));
        when(session.textMessage(anyString())).thenAnswer(invocation -> {
            String payload = invocation.getArgument(0, String.class);
            WebSocketMessage message = mock(WebSocketMessage.class);
            when(message.getType()).thenReturn(WebSocketMessage.Type.TEXT);
            when(message.getPayloadAsText()).thenReturn(payload);
            return message;
        });
        when(session.send(any())).thenAnswer(invocation -> Flux.from(invocation.<Publisher<WebSocketMessage>>getArgument(0))
                .doOnNext(message -> sentPayloads.add(message.getPayloadAsText()))
                .then());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> registry.size() == 1, "connection should be registered");
        waitUntil(() -> sentPayloads.size() == 1, "one outbound frame should be sent");

        assertEquals(
                "{\"type\":\"event\",\"requestId\":\"req-001\",\"sessionId\":\"session-001\",\"name\":\"chat.delta\"}",
                sentPayloads.getFirst()
        );
        verify(dispatcher, times(1)).outboundJson(anyString());

        subscription.dispose();
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
