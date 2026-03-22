package com.miniclaw.gateway.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.rpc.handler.ChatHandler;
import com.miniclaw.gateway.rpc.handler.RpcHandler;
import com.miniclaw.gateway.rpc.handler.SessionHandler;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcErrorFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcRouterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRouteSessionCreateToSessionHandler() {
        RecordingSessionHandler sessionHandler = new RecordingSessionHandler();
        RecordingChatHandler chatHandler = new RecordingChatHandler();
        RpcRouter router = new RpcRouter(List.of(sessionHandler, chatHandler));

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-session")
                .method("session.create")
                .payload(objectMapper.createObjectNode())
                .build();

        Object result = router.route("connection-1", request).block();

        assertEquals(1, sessionHandler.invocations);
        assertEquals(0, chatHandler.invocations);
        assertEquals("session.create", sessionHandler.lastRequest.getMethod());
        assertEquals("connection-1", sessionHandler.lastConnectionId);
        assertEquals("completed", ((RpcCompletedFrame) result).getType());
    }

    @Test
    void shouldRouteChatSendToChatHandler() {
        RecordingSessionHandler sessionHandler = new RecordingSessionHandler();
        RecordingChatHandler chatHandler = new RecordingChatHandler();
        RpcRouter router = new RpcRouter(List.of(sessionHandler, chatHandler));

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-chat")
                .sessionId("session-001")
                .method("chat.send")
                .payload(payload("message", "hello"))
                .build();

        Object result = router.route("connection-2", request).block();

        assertEquals(0, sessionHandler.invocations);
        assertEquals(1, chatHandler.invocations);
        assertEquals("chat.send", chatHandler.lastRequest.getMethod());
        assertEquals("connection-2", chatHandler.lastConnectionId);
        assertEquals("completed", ((RpcCompletedFrame) result).getType());
    }

    @Test
    void shouldReturnProtocolErrorForUnknownMethod() {
        RpcRouter router = new RpcRouter(List.of(new RecordingSessionHandler(), new RecordingChatHandler()));

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-missing")
                .sessionId("session-002")
                .method("chat.run")
                .payload(objectMapper.createObjectNode())
                .build();

        RpcErrorFrame result = (RpcErrorFrame) router.route("connection-3", request).block();

        assertEquals("error", result.getType());
        assertEquals("req-missing", result.getRequestId());
        assertEquals("session-002", result.getSessionId());
        assertEquals("METHOD_NOT_FOUND", result.getError().getCode());
    }

    private ObjectNode payload(String key, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(key, value);
        return payload;
    }

    private static final class RecordingSessionHandler implements SessionHandler {

        private int invocations;
        private String lastConnectionId;
        private RpcRequestFrame lastRequest;

        @Override
        public List<String> supportedMethods() {
            return List.of("session.create", "session.get", "session.close");
        }

        @Override
        public Mono<Object> handle(String connectionId, RpcRequestFrame request) {
            this.invocations++;
            this.lastConnectionId = connectionId;
            this.lastRequest = request;
            return Mono.just(RpcCompletedFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    null
            ));
        }
    }

    private static final class RecordingChatHandler implements ChatHandler {

        private int invocations;
        private String lastConnectionId;
        private RpcRequestFrame lastRequest;

        @Override
        public List<String> supportedMethods() {
            return List.of("chat.send");
        }

        @Override
        public Mono<Object> handle(String connectionId, RpcRequestFrame request) {
            this.invocations++;
            this.lastConnectionId = connectionId;
            this.lastRequest = request;
            return Mono.just(RpcCompletedFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    null
            ));
        }
    }
}
