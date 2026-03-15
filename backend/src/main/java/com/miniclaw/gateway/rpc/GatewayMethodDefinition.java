package com.miniclaw.gateway.rpc;

/**
 * 描述 Gateway 对外暴露的一个方法。
 */
public class GatewayMethodDefinition {

    private final String method;
    private final GatewayInvocationMode invocationMode;
    private final boolean requiresExistingSession;
    private final boolean createsSession;

    public GatewayMethodDefinition(String method,
                                   GatewayInvocationMode invocationMode,
                                   boolean requiresExistingSession,
                                   boolean createsSession) {
        this.method = method;
        this.invocationMode = invocationMode;
        this.requiresExistingSession = requiresExistingSession;
        this.createsSession = createsSession;
    }

    public String getMethod() {
        return method;
    }

    public GatewayInvocationMode getInvocationMode() {
        return invocationMode;
    }

    public boolean requiresExistingSession() {
        return requiresExistingSession;
    }

    public boolean createsSession() {
        return createsSession;
    }
}
