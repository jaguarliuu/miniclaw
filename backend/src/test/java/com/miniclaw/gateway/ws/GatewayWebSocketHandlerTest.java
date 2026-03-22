package com.miniclaw.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.event.GatewayEventBus;
import com.miniclaw.gateway.event.OutboundDispatcher;
import com.miniclaw.gateway.rpc.RpcRouter;
import com.miniclaw.gateway.rpc.handler.ChatHandler;
import com.miniclaw.gateway.rpc.handler.RpcHandler;
import com.miniclaw.gateway.rpc.handler.SessionHandler;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRegisterOnConnectAndRemoveOnDisconnect() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayEventBus eventBus = new GatewayEventBus();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                new RpcRouter(List.of(new RecordingSessionHandler())),
                eventBus,
                new OutboundDispatcher(eventBus, objectMapper),
                objectMapper
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
    void shouldRouteInboundRequestAndSendCompletedFrame() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayEventBus eventBus = new GatewayEventBus();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                new RpcRouter(List.of(new RecordingSessionHandler())),
                eventBus,
                new OutboundDispatcher(eventBus, objectMapper),
                objectMapper
        );

        WebSocketSession session = mock(WebSocketSession.class);
        List<String> sentPayloads = new ArrayList<>();
        WebSocketMessage inboundMessage = inboundTextMessage(
                "{\"type\":\"request\",\"requestId\":\"req-001\",\"method\":\"session.create\",\"payload\":{}}"
        );
        when(session.receive()).thenReturn(Flux.just(inboundMessage));
        when(session.textMessage(anyString())).thenAnswer(invocation -> outboundTextMessage(invocation.getArgument(0, String.class)));
        when(session.send(any())).thenAnswer(invocation -> Flux.from(invocation.<Publisher<WebSocketMessage>>getArgument(0))
                .doOnNext(message -> sentPayloads.add(message.getPayloadAsText()))
                .then());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> sentPayloads.size() == 1, "one completed frame should be sent");
        assertEquals(1, sentPayloads.size());
        assertTrue(sentPayloads.getFirst().contains("\"type\":\"completed\""));
        assertTrue(sentPayloads.getFirst().contains("\"requestId\":\"req-001\""));
        subscription.dispose();
    }

    @Test
    void shouldClearBoundSessionsWhenConnectionDisconnects() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        InMemorySessionRegistry sessionRegistry = mock(InMemorySessionRegistry.class);
        GatewayEventBus eventBus = new GatewayEventBus();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                sessionRegistry,
                new RpcRouter(List.of(new RecordingSessionHandler())),
                eventBus,
                new OutboundDispatcher(eventBus, objectMapper),
                objectMapper
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
                new RpcRouter(List.of(new RecordingSessionHandler())),
                new GatewayEventBus(),
                dispatcher
                ,
                objectMapper
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

    @Test
    void shouldReturnErrorFrameForMalformedInboundJson() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry();
        GatewayEventBus eventBus = new GatewayEventBus();
        GatewayWebSocketHandler handler = new GatewayWebSocketHandler(
                registry,
                new InMemorySessionRegistry(registry),
                new RpcRouter(List.of(new RecordingSessionHandler())),
                eventBus,
                new OutboundDispatcher(eventBus, objectMapper),
                objectMapper
        );

        WebSocketSession session = mock(WebSocketSession.class);
        List<String> sentPayloads = new ArrayList<>();
        WebSocketMessage inboundMessage = inboundTextMessage("{broken json}");
        when(session.receive()).thenReturn(Flux.just(inboundMessage));
        when(session.textMessage(anyString())).thenAnswer(invocation -> outboundTextMessage(invocation.getArgument(0, String.class)));
        when(session.send(any())).thenAnswer(invocation -> Flux.from(invocation.<Publisher<WebSocketMessage>>getArgument(0))
                .doOnNext(message -> sentPayloads.add(message.getPayloadAsText()))
                .then());

        Disposable subscription = handler.handle(session).subscribe();

        waitUntil(() -> sentPayloads.size() == 1, "one error frame should be sent");
        assertEquals(1, sentPayloads.size());
        assertTrue(sentPayloads.getFirst().contains("\"type\":\"error\""));
        assertTrue(sentPayloads.getFirst().contains("BAD_REQUEST"));
        subscription.dispose();
    }

    private WebSocketMessage inboundTextMessage(String payload) {
        WebSocketMessage message = mock(WebSocketMessage.class);
        when(message.getType()).thenReturn(WebSocketMessage.Type.TEXT);
        when(message.getPayloadAsText()).thenReturn(payload);
        return message;
    }

    private WebSocketMessage outboundTextMessage(String payload) {
        WebSocketMessage message = mock(WebSocketMessage.class);
        when(message.getPayloadAsText()).thenReturn(payload);
        return message;
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

    private static final class RecordingSessionHandler implements SessionHandler {

        @Override
        public List<String> supportedMethods() {
            return List.of("session.create");
        }

        @Override
        public Mono<Object> handle(String connectionId, RpcRequestFrame request) {
            ObjectNode payload = new ObjectMapper().createObjectNode();
            payload.put("created", true);
            return Mono.just(RpcCompletedFrame.of(request.getRequestId(), "session-generated", payload));
        }
    }
}
