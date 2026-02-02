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

            boolean stream = extractStream(request.getPayload());

            log.info("Testing LLM with prompt: {}, stream: {}", prompt, stream);

            LlmRequest llmRequest = LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user(prompt)))
                    .build();

            if (stream) {
                // 流式测试：收集所有内容后返回
                StringBuilder content = new StringBuilder();
                llmClient.stream(llmRequest)
                        .doOnNext(chunk -> {
                            if (chunk.getDelta() != null) {
                                content.append(chunk.getDelta());
                                log.info("Stream chunk: {}", chunk.getDelta());
                            }
                        })
                        .blockLast();

                return RpcResponse.success(request.getId(), Map.of(
                        "content", content.toString(),
                        "mode", "stream"
                ));
            } else {
                // 同步测试
                LlmResponse response = llmClient.chat(llmRequest);

                log.info("LLM response: {}", response.getContent());

                return RpcResponse.success(request.getId(), Map.of(
                        "content", response.getContent(),
                        "finishReason", response.getFinishReason() != null ? response.getFinishReason() : "unknown",
                        "mode", "sync"
                ));
            }
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

    private boolean extractStream(Object payload) {
        if (payload instanceof Map) {
            Object stream = ((Map<?, ?>) payload).get("stream");
            return Boolean.TRUE.equals(stream) || "true".equals(String.valueOf(stream));
        }
        return false;
    }
}
