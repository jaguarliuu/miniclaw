package com.miniclaw.gateway.rpc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 第 5 章的第一步，先把 Gateway 的统一能力面收口。
 * 后续 Router、Handler 和协议层都围绕这份目录扩展。
 */
public class GatewayMethodCatalog {

    private final Map<String, GatewayMethodDefinition> methods;

    public GatewayMethodCatalog() {
        this.methods = createDefaultMethods();
    }

    public Optional<GatewayMethodDefinition> find(String method) {
        if (method == null || method.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(methods.get(method));
    }

    public boolean supports(String method) {
        return find(method).isPresent();
    }

    public GatewayMethodDefinition getRequired(String method) {
        return find(method)
                .orElseThrow(() -> new IllegalArgumentException("Unknown gateway method: " + method));
    }

    private Map<String, GatewayMethodDefinition> createDefaultMethods() {
        Map<String, GatewayMethodDefinition> catalog = new LinkedHashMap<>();
        register(catalog, "session.create", GatewayInvocationMode.UNARY, false, true);
        register(catalog, "session.get", GatewayInvocationMode.UNARY, true, false);
        register(catalog, "session.close", GatewayInvocationMode.UNARY, true, false);
        register(catalog, "chat.send", GatewayInvocationMode.STREAMING, true, false);
        return Collections.unmodifiableMap(catalog);
    }

    private void register(Map<String, GatewayMethodDefinition> catalog,
                          String method,
                          GatewayInvocationMode invocationMode,
                          boolean requiresExistingSession,
                          boolean createsSession) {
        catalog.put(method, new GatewayMethodDefinition(
                method,
                invocationMode,
                requiresExistingSession,
                createsSession
        ));
    }
}
