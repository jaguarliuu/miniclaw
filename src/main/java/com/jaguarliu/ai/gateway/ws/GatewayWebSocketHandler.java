package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.gateway.rpc.RpcRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
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
    private final RpcRouter rpcRouter;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String connectionId = generateConnectionId();

        // 注册连接
        connectionManager.register(connectionId, session);

        // 处理接收到的消息并发送响应
        // agent.run 等需要排队的请求会立即返回 runId，结果通过事件推送
        Flux<WebSocketMessage> output = session.receive()
                .filter(message -> message.getType() == WebSocketMessage.Type.TEXT)
                .flatMap(message -> handleMessage(connectionId, session, message))
                .doOnError(error -> log.error("WebSocket error: connectionId={}", connectionId, error))
                .doFinally(signalType -> connectionManager.remove(connectionId));

        return session.send(output);
    }

    /**
     * 处理接收到的消息并返回响应
     */
    private Mono<WebSocketMessage> handleMessage(String connectionId, WebSocketSession session, WebSocketMessage message) {
        String payload = message.getPayloadAsText();
        log.debug("Received message: connectionId={}, payload={}", connectionId, payload);

        return rpcRouter.route(connectionId, payload)
                .map(session::textMessage);
    }

    /**
     * 生成连接 ID
     */
    private String generateConnectionId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
