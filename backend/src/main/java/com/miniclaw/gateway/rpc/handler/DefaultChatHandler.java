package com.miniclaw.gateway.rpc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.event.GatewayEvent;
import com.miniclaw.gateway.event.GatewayEventBus;
import com.miniclaw.gateway.rpc.model.RpcCompletedFrame;
import com.miniclaw.gateway.rpc.model.RpcErrorFrame;
import com.miniclaw.gateway.rpc.model.RpcEventFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.GatewaySession;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import com.miniclaw.gateway.session.SessionLane;
import com.miniclaw.gateway.session.SessionState;
import com.miniclaw.gateway.session.SessionStateMachine;
import com.miniclaw.llm.LlmClient;
import com.miniclaw.llm.model.LlmRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class DefaultChatHandler implements ChatHandler {

    private final InMemorySessionRegistry sessionRegistry;
    private final SessionStateMachine stateMachine;
    private final SessionLane sessionLane;
    private final GatewayEventBus eventBus;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public DefaultChatHandler(InMemorySessionRegistry sessionRegistry,
                              SessionStateMachine stateMachine,
                              SessionLane sessionLane,
                              GatewayEventBus eventBus,
                              LlmClient llmClient,
                              ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.stateMachine = stateMachine;
        this.sessionLane = sessionLane;
        this.eventBus = eventBus;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<String> supportedMethods() {
        return List.of("chat.send");
    }

    @Override
    public Mono<Object> handle(String connectionId, RpcRequestFrame request) {
        GatewaySession session = sessionRegistry.find(request.getSessionId()).orElse(null);
        if (session == null) {
            return Mono.just(RpcErrorFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    "SESSION_NOT_FOUND",
                    "Unknown session: " + request.getSessionId()
            ));
        }

        try {
            stateMachine.assertAllowsMethod(session, request.getMethod());
        } catch (IllegalStateException exception) {
            return Mono.just(RpcErrorFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    "INVALID_SESSION_STATE",
                    exception.getMessage()
            ));
        }

        String message = request.getPayload() == null ? null : request.getPayload().path("message").asText(null);
        if (message == null || message.isBlank()) {
            return Mono.just(RpcErrorFrame.of(
                    request.getRequestId(),
                    request.getSessionId(),
                    "INVALID_PAYLOAD",
                    "chat.send requires payload.message"
            ));
        }

        return sessionLane.submit(session.getSessionId(), () -> executeChat(connectionId, session, request, message));
    }

    private Mono<Object> executeChat(String connectionId,
                                     GatewaySession session,
                                     RpcRequestFrame request,
                                     String message) {
        return Mono.defer(() -> {
            stateMachine.transition(session, SessionState.RUNNING);

            return llmClient.stream(LlmRequest.builder()
                            .messages(List.of(LlmRequest.Message.user(message)))
                            .build())
                    .doOnNext(chunk -> publishDelta(connectionId, request, chunk.getDelta()))
                    .then(Mono.fromSupplier(() -> (Object) RpcCompletedFrame.of(
                            request.getRequestId(),
                            request.getSessionId(),
                            null
                    )))
                    .onErrorResume(exception -> Mono.just(RpcErrorFrame.of(
                            request.getRequestId(),
                            request.getSessionId(),
                            "CHAT_STREAM_FAILED",
                            exception.getMessage()
                    )))
                    .doFinally(ignored -> resetToIdle(session));
        });
    }

    private void publishDelta(String connectionId, RpcRequestFrame request, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("delta", delta);
        eventBus.publish(GatewayEvent.outbound(
                connectionId,
                request.getSessionId(),
                request.getRequestId(),
                RpcEventFrame.of(request.getRequestId(), request.getSessionId(), "chat.delta", payload)
        ));
    }

    private void resetToIdle(GatewaySession session) {
        if (session.getState() == SessionState.RUNNING) {
            stateMachine.transition(session, SessionState.IDLE);
        }
    }
}
