package com.miniclaw.llm;

import com.miniclaw.llm.model.LlmRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        classes = OpenAiCompatibleLlmClientMultimodalTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class OpenAiCompatibleLlmClientMultimodalGuardTest {

    @Autowired
    private OpenAiCompatibleLlmClient client;

    @Test
    void deepseekProviderRejectsImageRequestBeforeCallingRemoteApi() {
        LlmException exception = assertThrows(LlmException.class, () -> client.chat(LlmRequest.builder()
                .providerId("deepseek")
                .messages(List.of(LlmRequest.Message.userWithImage(
                        "What is in this image?",
                        "https://example.com/demo.png"
                )))
                .build()));

        assertEquals(LlmErrorType.BAD_REQUEST, exception.getErrorType());
        assertEquals("Provider deepseek does not have a multimodal model configured", exception.getMessage());
    }
}
