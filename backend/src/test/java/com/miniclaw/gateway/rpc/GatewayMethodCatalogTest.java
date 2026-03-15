package com.miniclaw.gateway.rpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayMethodCatalogTest {

    private final GatewayMethodCatalog catalog = new GatewayMethodCatalog();

    @Test
    void shouldExposeUnifiedGatewayEntryMethods() {
        assertTrue(catalog.supports("session.create"));
        assertTrue(catalog.supports("session.get"));
        assertTrue(catalog.supports("session.close"));
        assertTrue(catalog.supports("chat.send"));
    }

    @Test
    void chatSendShouldBeStreamingAndRequireExistingSession() {
        GatewayMethodDefinition definition = catalog.getRequired("chat.send");

        assertEquals(GatewayInvocationMode.STREAMING, definition.getInvocationMode());
        assertTrue(definition.requiresExistingSession());
        assertFalse(definition.createsSession());
    }

    @Test
    void sessionCreateShouldBootstrapSessionWithUnaryCall() {
        GatewayMethodDefinition definition = catalog.getRequired("session.create");

        assertEquals(GatewayInvocationMode.UNARY, definition.getInvocationMode());
        assertFalse(definition.requiresExistingSession());
        assertTrue(definition.createsSession());
    }

    @Test
    void shouldReturnEmptyForUnknownMethod() {
        assertFalse(catalog.find("llm.chat").isPresent());
    }
}
