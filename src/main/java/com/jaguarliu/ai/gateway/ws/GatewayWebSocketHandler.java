package com.jaguarliu.ai.gateway.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebSocket 处理器
 * 处理 WebSocket 连接的建立、消息收发、断开
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayWebSocketHandler implements WebSocketHandler {

    private final ConnectionManager connectionManager;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String connectionId = generateConnectionId();

        // 注册连接
        connectionManager.register(connectionId, session);

        // 处理接收到的消息
        Mono<Void> input = session.receive()
                .doOnNext(message -> handleMessage(connectionId, message))
                .doOnError(error -> log.error("WebSocket error: connectionId={}", connectionId, error))
                .doFinally(signalType -> connectionManager.remove(connectionId))
                .then();

        return input;
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String connectionId, WebSocketMessage message) {
        if (message.getType() == WebSocketMessage.Type.TEXT) {
            String payload = message.getPayloadAsText();
            log.debug("Received message: connectionId={}, payload={}", connectionId, payload);
            // TODO: P1-03 实现 RPC Router 分发
        }
    }

    /**
     * 生成连接 ID
     */
    private String generateConnectionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
