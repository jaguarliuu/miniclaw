package com.miniclaw.gateway.session;

import java.time.Instant;

/**
 * Gateway 层的业务会话。
 * 5.3 先只保留最小元数据，状态机会在后续小节再补进来。
 */
public class GatewaySession {

    private final String sessionId;
    private final String connectionId;
    private final Instant createdAt;

    public GatewaySession(String sessionId, String connectionId, Instant createdAt) {
        this.sessionId = sessionId;
        this.connectionId = connectionId;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
