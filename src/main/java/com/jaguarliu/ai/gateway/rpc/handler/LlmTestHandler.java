package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * LLM 测试处理器（临时，用于验证 LLM 连通性）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTestHandler implements RpcHandler {

    private final LlmClient llmClient;

    @Override
    public String getMethod() {
        return "llm.test";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String prompt = extractPrompt(request.getPayload());
            if (prompt == null || prompt.isBlank()) {
                prompt = "你好，请简单介绍一下你自己。";
            }

            log.info("Testing LLM with prompt: {}", prompt);

            LlmRequest llmRequest = LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user(prompt)))
                    .build();

            LlmResponse response = llmClient.chat(llmRequest);

            log.info("LLM response: {}", response.getContent());

            return RpcResponse.success(request.getId(), Map.of(
                    "content", response.getContent(),
                    "finishReason", response.getFinishReason() != null ? response.getFinishReason() : "unknown",
                    "usage", response.getUsage() != null ? Map.of(
                            "promptTokens", response.getUsage().getPromptTokens(),
                            "completionTokens", response.getUsage().getCompletionTokens(),
                            "totalTokens", response.getUsage().getTotalTokens()
                    ) : null
            ));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("LLM test failed", e);
              return Mono.just(RpcResponse.error(request.getId(), "LLM_ERROR", e.getMessage()));
          });
    }

    private String extractPrompt(Object payload) {
        if (payload instanceof Map) {
            Object prompt = ((Map<?, ?>) payload).get("prompt");
            return prompt != null ? prompt.toString() : null;
        }
        return null;
    }
}
