package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 应用状态 RPC handler — app.status
 * 返回应用运行状态，包括 LLM 是否已配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppStatusHandler implements RpcHandler {

    private final LlmConfigService llmConfigService;

    @Override
    public String getMethod() {
        return "app.status";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
                RpcResponse.success(request.getId(), Map.of(
                        "llmConfigured", llmConfigService.isConfigured()
                ))
        );
    }
}
