package com.miniclaw.gateway.event;

/**
 * Gateway 内部的出站事件。
 */
public class GatewayEvent {

    private final String connectionId;
    private final String sessionId;
    private final String requestId;
    private final Object frame;

    public GatewayEvent(String connectionId, String sessionId, String requestId, Object frame) {
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.frame = frame;
    }

    public static GatewayEvent outbound(String connectionId, String sessionId, String requestId, Object frame) {
        return new GatewayEvent(connectionId, sessionId, requestId, frame);
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Object getFrame() {
        return frame;
    }
}
