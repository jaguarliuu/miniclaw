package com.miniclaw.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.event.GatewayEvent;
import com.miniclaw.gateway.event.GatewayEventBus;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcErrorFrame;
import com.miniclaw.gateway.rpc.model.RpcEventFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.GatewaySession;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import com.miniclaw.gateway.session.PersistentSessionService;
import com.miniclaw.gateway.session.SessionLane;
import com.miniclaw.gateway.session.SessionState;
import com.miniclaw.gateway.session.SessionStateMachine;
import com.miniclaw.gateway.session.persistence.SessionEntity;
import com.miniclaw.gateway.session.persistence.SessionEntityRepository;
import com.miniclaw.llm.LlmClient;
import com.miniclaw.llm.model.LlmChunk;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultChatHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPublishChatDeltaEventsAndReturnCompletedFrame() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        when(repository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PersistentSessionService sessionService = new PersistentSessionService(sessionRegistry, repository);
        GatewaySession session = sessionService.create(connection.getConnectionId());
        GatewayEventBus eventBus = new GatewayEventBus();
        RecordingLlmClient llmClient = new RecordingLlmClient(Flux.just(
                LlmChunk.builder().delta("hel").done(false).build(),
                LlmChunk.builder().delta("lo").done(false).build()
        ));

        DefaultChatHandler handler = new DefaultChatHandler(
                sessionService,
                new SessionStateMachine(),
                new SessionLane(),
                eventBus,
                llmClient,
                objectMapper
        );

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-chat-001")
                .sessionId(session.getSessionId())
                .method("chat.send")
                .payload(payload("message", "hello"))
                .build();

        List<GatewayEvent> events = eventBus.events()
                .take(2)
                .timeout(Duration.ofSeconds(1))
                .collectList()
                .doOnSubscribe(ignored -> {
                    RpcCompletedFrame completed = (RpcCompletedFrame) handler.handle(connection.getConnectionId(), request)
                            .block(Duration.ofSeconds(1));
                    assertEquals("completed", completed.getType());
                    assertEquals(session.getSessionId(), completed.getSessionId());
                })
                .block();

        assertEquals(2, events.size());
        assertEquals("chat.delta", ((RpcEventFrame) events.get(0).getFrame()).getName());
        assertEquals("hel", ((RpcEventFrame) events.get(0).getFrame()).getPayload().get("delta").asText());
        assertEquals("lo", ((RpcEventFrame) events.get(1).getFrame()).getPayload().get("delta").asText());
        assertEquals(SessionState.IDLE, sessionService.find(session.getSessionId()).orElseThrow().getState());
        assertEquals("hello", llmClient.lastRequest.getMessages().getFirst().getContent());
        verify(repository, org.mockito.Mockito.atLeast(3)).save(any(SessionEntity.class));
    }

    @Test
    void shouldReturnErrorWhenSessionIsClosed() {
        ConnectionRegistry connectionRegistry = new ConnectionRegistry();
        ConnectionContext connection = connectionRegistry.register(mock(WebSocketSession.class));
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry(connectionRegistry);
        SessionEntityRepository repository = mock(SessionEntityRepository.class);
        when(repository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PersistentSessionService sessionService = new PersistentSessionService(sessionRegistry, repository);
        GatewaySession session = sessionService.create(connection.getConnectionId());
        session.setState(SessionState.CLOSED);
        sessionService.save(session);

        DefaultChatHandler handler = new DefaultChatHandler(
                sessionService,
                new SessionStateMachine(),
                new SessionLane(),
                new GatewayEventBus(),
                new RecordingLlmClient(Flux.empty()),
                objectMapper
        );

        RpcRequestFrame request = RpcRequestFrame.builder()
                .requestId("req-chat-closed")
                .sessionId(session.getSessionId())
                .method("chat.send")
                .payload(payload("message", "hello"))
                .build();

        RpcErrorFrame result = (RpcErrorFrame) handler.handle(connection.getConnectionId(), request)
                .block(Duration.ofSeconds(1));

        assertEquals("error", result.getType());
        assertEquals("INVALID_SESSION_STATE", result.getError().getCode());
    }

    private ObjectNode payload(String key, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(key, value);
        return payload;
    }

    private static final class RecordingLlmClient implements LlmClient {

        private final Flux<LlmChunk> response;
        private LlmRequest lastRequest;

        private RecordingLlmClient(Flux<LlmChunk> response) {
            this.response = response;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<LlmChunk> stream(LlmRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
