package com.miniclaw.llm;

import com.miniclaw.config.LlmProviderConfig;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmProviderRegistryTest {

    @Test
    void resolvesDefaultProviderFromDefaultModel() {
        LlmProviderRegistry registry = new LlmProviderRegistry(createProperties());

        ResolvedLlmContext context = registry.resolve(LlmRequest.builder().build());

        assertEquals("deepseek", context.getProviderId());
        assertEquals("deepseek-chat", context.getProvider().getDefaultModel());
        assertNotNull(context.getClient());
    }

    @Test
    void resolvesRequestedProviderId() {
        LlmProviderRegistry registry = new LlmProviderRegistry(createProperties());

        ResolvedLlmContext context = registry.resolve(LlmRequest.builder()
                .providerId("qwen")
                .build());

        assertEquals("qwen", context.getProviderId());
        assertEquals("qwen3-vl-plus", context.getProvider().getDefaultMultimodalModel());
        assertNotNull(context.getClient());
    }

    @Test
    void rejectsUnknownProvider() {
        LlmProviderRegistry registry = new LlmProviderRegistry(createProperties());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                LlmRequest.builder()
                        .providerId("unknown")
                        .build()
        ));

        assertEquals("Unknown LLM provider: unknown", exception.getMessage());
    }

    private LlmProperties createProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultModel("deepseek:deepseek-chat");
        properties.setProviders(List.of(
                LlmProviderConfig.builder()
                        .id("deepseek")
                        .endpoint("https://api.deepseek.com")
                        .apiKey("deepseek-key")
                        .models(List.of("deepseek-chat", "deepseek-reasoner"))
                        .build(),
                LlmProviderConfig.builder()
                        .id("qwen")
                        .endpoint("https://dashscope.aliyuncs.com/compatible-mode/v1")
                        .apiKey("qwen-key")
                        .models(List.of("qwen3.5-plus", "qwen3.5-flash"))
                        .multimodalModels(List.of("qwen3-vl-plus"))
                        .build()
        ));
        return properties;
    }
}
