package com.miniclaw.gateway.ws;

import com.miniclaw.gateway.connection.ConnectionContext;
import com.miniclaw.gateway.connection.ConnectionRegistry;
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

    public GatewayWebSocketHandler(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        ConnectionContext connection = connectionRegistry.register(session);
        log.info("Gateway websocket connected: connectionId={}", connection.getConnectionId());

        return session.receive()
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .doOnNext(message -> log.debug(
                        "Gateway received inbound text frame before router is ready: connectionId={}, payload={}",
                        connection.getConnectionId(),
                        message.getPayloadAsText()
                ))
                .then()
                .doFinally(signalType -> {
                    connectionRegistry.remove(connection.getConnectionId());
                    log.info("Gateway websocket disconnected: connectionId={}, signal={}",
                            connection.getConnectionId(), signalType);
                });
    }
}
