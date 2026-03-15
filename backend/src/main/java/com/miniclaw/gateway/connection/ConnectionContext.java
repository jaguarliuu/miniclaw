package com.miniclaw.gateway.connection;

import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个 WebSocket 物理连接的运行时上下文。
 * 第 5.2 节先只关心连接本身，以及这个连接绑定了哪些业务 session。
 */
public class ConnectionContext {

    private final String connectionId;
    private final WebSocketSession webSocketSession;
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    public ConnectionContext(String connectionId, WebSocketSession webSocketSession) {
        this.connectionId = connectionId;
        this.webSocketSession = webSocketSession;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public Set<String> getSessionIds() {
        return Collections.unmodifiableSet(sessionIds);
    }

    public void bindSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionIds.add(sessionId);
        }
    }

    public void unbindSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionIds.remove(sessionId);
        }
    }
}
