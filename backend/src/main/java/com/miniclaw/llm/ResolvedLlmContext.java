package com.miniclaw.llm;

import com.miniclaw.config.LlmProviderConfig;
import org.springframework.web.reactive.function.client.WebClient;

class ResolvedLlmContext {

    private final String providerId;
    private final LlmProviderConfig provider;
    private final WebClient client;
    private final boolean legacyMode;

    ResolvedLlmContext(String providerId, LlmProviderConfig provider, WebClient client, boolean legacyMode) {
        this.providerId = providerId;
        this.provider = provider;
        this.client = client;
        this.legacyMode = legacyMode;
    }

    String getProviderId() {
        return providerId;
    }

    LlmProviderConfig getProvider() {
        return provider;
    }

    WebClient getClient() {
        return client;
    }

    boolean isLegacyMode() {
        return legacyMode;
    }
}
