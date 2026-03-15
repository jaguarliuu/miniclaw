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
        classes = OpenAiCompatibleLlmClientMultiProviderLiveTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class OpenAiCompatibleLlmClientMultiProviderLiveTest {

    private static final String DEEPSEEK_PROVIDER_ID = "deepseek";
    private static final String QWEN_PROVIDER_ID = "qwen";

    @Autowired
    private OpenAiCompatibleLlmClient client;

    @Test
    void deepseekProviderIdCanHandleChatAndStream() {
        assumeTrue(hasText(System.getenv("DEEPSEEK_API_KEY")),
                "未检测到 DEEPSEEK_API_KEY，跳过 deepseek live demo");

        LlmResponse chatResponse = client.chat(demoRequest(DEEPSEEK_PROVIDER_ID,
                "你是 4.9 小节的演示助手。只用一句简短中文回答：当前走的是 deepseek provider。"));

        String streamText = collectStreamText(DEEPSEEK_PROVIDER_ID,
                "你是 4.9 小节的演示助手。只回复一个很短的中文短语，不要解释。");

        assertNotNull(chatResponse);
        assertTrue(hasText(chatResponse.getContent()), "deepseek 的 chat 响应为空");
        assertFalse(streamText.isBlank(), "deepseek 的 stream 没有产出文本");

        System.out.printf("[4.9 live demo] provider=%s chat=%s%n",
                DEEPSEEK_PROVIDER_ID, chatResponse.getContent());
        System.out.printf("[4.9 live demo] provider=%s stream=%s%n",
                DEEPSEEK_PROVIDER_ID, streamText);
    }

    @Test
    void qwenProviderIdCanHandleChatAndStream() {
        assumeTrue(hasText(System.getenv("QWEN_API_KEY")),
                "未检测到 QWEN_API_KEY，跳过 qwen live demo");

        LlmResponse chatResponse = client.chat(demoRequest(QWEN_PROVIDER_ID,
                "你是 4.9 小节的演示助手。只用一句简短中文回答：当前走的是 qwen provider。"));

        String streamText = collectStreamText(QWEN_PROVIDER_ID,
                "你是 4.9 小节的演示助手。只回复一个很短的中文短语，不要解释。");

        assertNotNull(chatResponse);
        assertTrue(hasText(chatResponse.getContent()), "qwen 的 chat 响应为空");
        assertFalse(streamText.isBlank(), "qwen 的 stream 没有产出文本");

        System.out.printf("[4.9 live demo] provider=%s chat=%s%n",
                QWEN_PROVIDER_ID, chatResponse.getContent());
        System.out.printf("[4.9 live demo] provider=%s stream=%s%n",
                QWEN_PROVIDER_ID, streamText);
    }

    private String collectStreamText(String providerId, String prompt) {
        StringBuilder builder = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);

        StepVerifier.create(client.stream(demoRequest(providerId, prompt)))
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

        assertTrue(finished.get(), "流式响应没有正常结束");
        return builder.toString().trim();
    }

    private LlmRequest demoRequest(String providerId, String prompt) {
        return LlmRequest.builder()
                .providerId(providerId)
                .temperature(0.0)
                .maxTokens(64)
                .messages(List.of(LlmRequest.Message.user(prompt)))
                .build();
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
