package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmChunk;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.ToolCall;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * llm.test.tools 处理器
 * 测试 Function Calling 功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmTestToolsHandler implements RpcHandler {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    @Override
    public String getMethod() {
        return "llm.test.tools";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String prompt = extractPrompt(request.getPayload());
        if (prompt == null || prompt.isBlank()) {
            prompt = "请使用 ping 工具测试一下，消息是 'hello'";
        }

        String finalPrompt = prompt;

        return Mono.fromCallable(() -> {
            // 构建带 tools 的请求
            LlmRequest llmRequest = LlmRequest.builder()
                    .messages(List.of(
                            LlmRequest.Message.system("你是一个助手。当需要时请使用提供的工具。"),
                            LlmRequest.Message.user(finalPrompt)
                    ))
                    .tools(toolRegistry.toOpenAiTools())
                    .toolChoice("auto")
                    .build();

            log.info("Testing function calling with prompt: {}", finalPrompt);
            log.debug("Tools: {}", toolRegistry.toOpenAiTools());

            // 流式调用并收集结果
            StringBuilder content = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            llmClient.stream(llmRequest)
                    .doOnNext(chunk -> {
                        if (chunk.getDelta() != null) {
                            content.append(chunk.getDelta());
                        }
                        if (chunk.hasToolCalls()) {
                            toolCalls.addAll(chunk.getToolCalls());
                        }
                    })
                    .blockLast();

            // 构建响应
            Map<String, Object> result = new HashMap<>();
            result.put("content", content.toString());
            result.put("hasToolCalls", !toolCalls.isEmpty());

            if (!toolCalls.isEmpty()) {
                List<Map<String, Object>> tcList = toolCalls.stream()
                        .map(tc -> Map.<String, Object>of(
                                "id", tc.getId(),
                                "name", tc.getName(),
                                "arguments", tc.getArguments()
                        ))
                        .toList();
                result.put("toolCalls", tcList);
            }

            log.info("Function calling test result: content={}, toolCalls={}",
                    content.length() > 0 ? content.toString().substring(0, Math.min(50, content.length())) + "..." : "(empty)",
                    toolCalls.size());

            return RpcResponse.success(request.getId(), result);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractPrompt(Object payload) {
        if (payload instanceof Map) {
            Object p = ((Map<?, ?>) payload).get("prompt");
            return p != null ? p.toString() : null;
        }
        return null;
    }
}
