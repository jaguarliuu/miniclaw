package com.miniclaw.gateway.session;

import com.miniclaw.gateway.connection.ConnectionRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务 session 的内存态注册表。
 * 它和 ConnectionRegistry 配合工作：
 * ConnectionRegistry 管物理连接，SessionRegistry 管业务会话。
 */
@Component
public class InMemorySessionRegistry {

    private final ConnectionRegistry connectionRegistry;
    private final ConcurrentHashMap<String, GatewaySession> sessions = new ConcurrentHashMap<>();

    public InMemorySessionRegistry(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public GatewaySession create(String connectionId) {
        connectionRegistry.find(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown connection: " + connectionId));

        String sessionId = UUID.randomUUID().toString();
        GatewaySession session = new GatewaySession(sessionId, connectionId, Instant.now(), SessionState.IDLE);
        sessions.put(sessionId, session);
        connectionRegistry.bindSession(connectionId, sessionId);
        return session;
    }

    public Optional<GatewaySession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public List<GatewaySession> findByConnection(String connectionId) {
        return sessions.values().stream()
                .filter(session -> session.getConnectionId().equals(connectionId))
                .toList();
    }

    public Optional<GatewaySession> remove(String sessionId) {
        GatewaySession removed = sessions.remove(sessionId);
        if (removed != null) {
            connectionRegistry.unbindSession(removed.getConnectionId(), removed.getSessionId());
        }
        return Optional.ofNullable(removed);
    }

    public void removeAllByConnection(String connectionId) {
        List<String> sessionIds = new ArrayList<>(findByConnection(connectionId).stream()
                .map(GatewaySession::getSessionId)
                .toList());

        sessionIds.forEach(sessionId -> {
            GatewaySession removed = sessions.remove(sessionId);
            if (removed != null) {
                connectionRegistry.unbindSession(connectionId, sessionId);
            }
        });
    }

    public int size() {
        return sessions.size();
    }
}
