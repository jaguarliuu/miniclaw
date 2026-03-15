package com.miniclaw.llm;

import com.miniclaw.config.LlmProviderConfig;
import com.miniclaw.config.LlmProperties;
import com.miniclaw.llm.model.LlmRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmRequestMapperTest {

    @Test
    void usesProvidersDefaultTextModelWhenModelIsOmitted() {
        LlmRequestMapper mapper = new LlmRequestMapper(createProperties());

        OpenAiChatCompletionRequest request = mapper.map(
                LlmRequest.builder()
                        .providerId("qwen")
                        .messages(List.of(LlmRequest.Message.user("hello")))
                        .build(),
                qwenContext(),
                false
        );

        assertEquals("qwen3.5-plus", request.getModel());
        assertEquals(Boolean.FALSE, request.getStream());
        assertEquals("hello", request.getMessages().get(0).getContent());
    }

    @Test
    void usesProvidersDefaultMultimodalModelWhenImageRequestOmitsModel() {
        LlmRequestMapper mapper = new LlmRequestMapper(createProperties());

        OpenAiChatCompletionRequest request = mapper.map(
                LlmRequest.builder()
                        .providerId("qwen")
                        .messages(List.of(LlmRequest.Message.userWithImage(
                                "Describe this image.",
                                "https://example.com/cat.png"
                        )))
                        .build(),
                qwenContext(),
                true
        );

        assertEquals("qwen3-vl-plus", request.getModel());
        assertEquals(Boolean.TRUE, request.getStream());
        assertInstanceOf(List.class, request.getMessages().get(0).getContent());
    }

    @Test
    void rejectsImageRequestWithNonMultimodalModel() {
        LlmRequestMapper mapper = new LlmRequestMapper(createProperties());

        LlmException exception = assertThrows(LlmException.class, () -> mapper.map(
                LlmRequest.builder()
                        .providerId("qwen")
                        .model("qwen3.5-plus")
                        .messages(List.of(LlmRequest.Message.userWithImage(
                                "Describe this image.",
                                "https://example.com/cat.png"
                        )))
                        .build(),
                qwenContext(),
                false
        ));

        assertEquals(LlmErrorType.BAD_REQUEST, exception.getErrorType());
        assertEquals("Provider qwen does not support multimodal model qwen3.5-plus", exception.getMessage());
    }

    @Test
    void preservesToolCallingFields() {
        LlmRequestMapper mapper = new LlmRequestMapper(createProperties());

        OpenAiChatCompletionRequest request = mapper.map(
                LlmRequest.builder()
                        .providerId("qwen")
                        .messages(List.of(LlmRequest.Message.user("What's the weather?")))
                        .tools(List.of(Map.of(
                                "type", "function",
                                "function", Map.of("name", "get_weather")
                        )))
                        .toolChoice("required")
                        .build(),
                qwenContext(),
                false
        );

        assertEquals("required", request.getToolChoice());
        assertEquals(1, request.getTools().size());
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

    private ResolvedLlmContext qwenContext() {
        LlmProviderConfig provider = createProperties().getProvider("qwen");
        return new ResolvedLlmContext(
                "qwen",
                provider,
                WebClient.builder().baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1").build(),
                false
        );
    }
}
