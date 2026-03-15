package com.miniclaw.gateway.connection;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 Gateway 当前活跃的 WebSocket 连接。
 */
@Component
public class ConnectionRegistry {

    private final ConcurrentHashMap<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionOwners = new ConcurrentHashMap<>();

    public ConnectionContext register(WebSocketSession session) {
        String connectionId = UUID.randomUUID().toString();
        ConnectionContext context = new ConnectionContext(connectionId, session);
        connections.put(connectionId, context);
        return context;
    }

    public void remove(String connectionId) {
        ConnectionContext removed = connections.remove(connectionId);
        if (removed == null) {
            return;
        }

        removed.getSessionIds().forEach(sessionOwners::remove);
    }

    public Optional<ConnectionContext> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public Optional<ConnectionContext> findBySessionId(String sessionId) {
        String connectionId = sessionOwners.get(sessionId);
        if (connectionId == null) {
            return Optional.empty();
        }
        return find(connectionId);
    }

    public void bindSession(String connectionId, String sessionId) {
        ConnectionContext context = find(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));

        String existingOwner = sessionOwners.putIfAbsent(sessionId, connectionId);
        if (existingOwner != null && !existingOwner.equals(connectionId)) {
            throw new IllegalStateException("Session " + sessionId + " is already bound to connection " + existingOwner);
        }

        context.bindSession(sessionId);
    }

    public void unbindSession(String connectionId, String sessionId) {
        ConnectionContext context = find(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));

        context.unbindSession(sessionId);
        sessionOwners.remove(sessionId, connectionId);
    }

    public int size() {
        return connections.size();
    }
}
