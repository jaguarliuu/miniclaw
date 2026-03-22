package com.miniclaw.gateway.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
import com.miniclaw.gateway.event.GatewayEvent;
import com.miniclaw.gateway.event.GatewayEventBus;
import com.miniclaw.gateway.event.OutboundDispatcher;
import com.miniclaw.gateway.rpc.RpcRouter;
import com.miniclaw.gateway.rpc.model.RpcErrorFrame;
import com.miniclaw.gateway.rpc.model.RpcRequestFrame;
import com.miniclaw.gateway.session.InMemorySessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * 第 5.2 节先把 Gateway 入口立住。
 * 当前只负责连接注册、最小入站处理和连接清理，
 * Router、协议解析和出站事件在后续小节再接进来。
 */
@Slf4j
@Component
public class GatewayWebSocketHandler implements WebSocketHandler {

    private final ConnectionRegistry connectionRegistry;
    private final InMemorySessionRegistry sessionRegistry;
    private final RpcRouter rpcRouter;
    private final GatewayEventBus eventBus;
    private final OutboundDispatcher outboundDispatcher;
    private final ObjectMapper objectMapper;

    public GatewayWebSocketHandler(ConnectionRegistry connectionRegistry,
                                   InMemorySessionRegistry sessionRegistry,
                                   RpcRouter rpcRouter,
                                   GatewayEventBus eventBus,
                                   OutboundDispatcher outboundDispatcher,
                                   ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.sessionRegistry = sessionRegistry;
        this.rpcRouter = rpcRouter;
        this.eventBus = eventBus;
        this.outboundDispatcher = outboundDispatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        ConnectionContext connection = connectionRegistry.register(session);
        log.info("Gateway websocket connected: connectionId={}", connection.getConnectionId());

        Mono<Void> inbound = session.receive()
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .flatMap(message -> handleInboundText(connection.getConnectionId(), message.getPayloadAsText()))
                .then();

        Mono<Void> outbound = session.send(
                outboundDispatcher.outboundJson(connection.getConnectionId())
                        .map(session::textMessage)
        );

        return Mono.when(inbound, outbound)
                .doFinally(signalType -> {
                    sessionRegistry.removeAllByConnection(connection.getConnectionId());
                    connectionRegistry.remove(connection.getConnectionId());
                    log.info("Gateway websocket disconnected: connectionId={}, signal={}",
                            connection.getConnectionId(), signalType);
                });
    }

    private Mono<Void> handleInboundText(String connectionId, String payload) {
        RpcRequestFrame request;
        try {
            request = objectMapper.readValue(payload, RpcRequestFrame.class);
        } catch (JsonProcessingException exception) {
            publishFrame(connectionId, RpcErrorFrame.of(
                    null,
                    null,
                    "BAD_REQUEST",
                    "Malformed rpc request json"
            ));
            return Mono.empty();
        }

        return rpcRouter.route(connectionId, request)
                .doOnNext(frame -> publishFrame(connectionId, frame))
                .then();
    }

    private void publishFrame(String connectionId, Object frame) {
        String requestId = null;
        String sessionId = null;

        if (frame instanceof RpcErrorFrame errorFrame) {
            requestId = errorFrame.getRequestId();
            sessionId = errorFrame.getSessionId();
        } else if (frame instanceof com.miniclaw.gateway.rpc.model.RpcCompletedFrame completedFrame) {
            requestId = completedFrame.getRequestId();
            sessionId = completedFrame.getSessionId();
        }

        eventBus.publish(GatewayEvent.outbound(connectionId, sessionId, requestId, frame));
    }
}
