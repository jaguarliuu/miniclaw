package com.jaguarliu.ai.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接管理器
 * 维护 connectionId → WebSocketSession 映射
 */
@Slf4j
@Component
public class ConnectionManager {

    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();

    /**
     * 注册连接
     */
    public void register(String connectionId, WebSocketSession session) {
        connections.put(connectionId, session);
        log.info("WebSocket connected: connectionId={}, total={}", connectionId, connections.size());
    }

    /**
     * 移除连接
     */
    public void remove(String connectionId) {
        connections.remove(connectionId);
        log.info("WebSocket disconnected: connectionId={}, total={}", connectionId, connections.size());
    }

    /**
     * 获取连接
     */
    public WebSocketSession get(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return connections.size();
    }
}
