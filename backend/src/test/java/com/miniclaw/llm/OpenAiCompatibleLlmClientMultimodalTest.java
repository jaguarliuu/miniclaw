package com.miniclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import com.miniclaw.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(
        classes = OpenAiCompatibleLlmClientMultimodalTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class OpenAiCompatibleLlmClientMultimodalTest {

    private static final String QWEN_PROVIDER_ID = "qwen";

    /**
     * A tiny 1x1 PNG data URL. Using Data URL avoids teaching tests that depend on
     * the model provider fetching a third-party image over the network.
     */
    private static final String DEMO_IMAGE_DATA_URL =
            "data:image/png;base64,"
                    + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4"
                    + "//8/AwAI/AL+X2VINwAAAABJRU5ErkJggg==";

    @Autowired
    private OpenAiCompatibleLlmClient client;

    @Autowired
    private LlmProperties properties;

    @Test
    void qwenProviderIdCanHandleImageChat() {
        assumeTrue(hasUsableQwenApiKey(),
                "Qwen provider is not configured with a real API key, skip multimodal live demo");

        LlmResponse response = client.chat(multimodalRequest(
                "Describe the image in one short sentence.",
                DEMO_IMAGE_DATA_URL
        ));

        assertNotNull(response);
        assertTrue(hasText(response.getContent()), "Qwen multimodal chat returned blank content");

        System.out.printf("[4.10 live demo] provider=%s mode=chat result=%s%n",
                QWEN_PROVIDER_ID, response.getContent());
    }

    @Test
    void qwenProviderIdCanHandleImageStream() {
        assumeTrue(hasUsableQwenApiKey(),
                "Qwen provider is not configured with a real API key, skip multimodal live demo");

        String streamText = collectStreamText(multimodalRequest(
                "Describe the image in a short sentence.",
                DEMO_IMAGE_DATA_URL
        ));

        assertFalse(streamText.isBlank(), "Qwen multimodal stream did not emit text");

        System.out.printf("[4.10 live demo] provider=%s mode=stream result=%s%n",
                QWEN_PROVIDER_ID, streamText);
    }

    private LlmRequest multimodalRequest(String prompt, String imageUrl) {
        return LlmRequest.builder()
                .providerId(QWEN_PROVIDER_ID)
                .temperature(0.0)
                .maxTokens(96)
                .messages(List.of(LlmRequest.Message.userWithImage(prompt, imageUrl)))
                .build();
    }

    private String collectStreamText(LlmRequest request) {
        StringBuilder builder = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);

        StepVerifier.create(client.stream(request))
                .thenConsumeWhile(chunk -> {
                    if (hasText(chunk.getDelta())) {
                        builder.append(chunk.getDelta());
                    }
                    if (chunk.isDone()) {
                        finished.set(true);
                    }
                    return true;
                })
                .verifyComplete();

        assertTrue(finished.get(), "Multimodal stream did not finish normally");
        return builder.toString().trim();
    }

    private boolean hasUsableQwenApiKey() {
        if (properties.getProvider(QWEN_PROVIDER_ID) == null) {
            return false;
        }

        String apiKey = properties.getProvider(QWEN_PROVIDER_ID).getApiKey();
        return hasText(apiKey) && !apiKey.startsWith("your-");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(LlmProperties.class)
    @Import(OpenAiCompatibleLlmClient.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
