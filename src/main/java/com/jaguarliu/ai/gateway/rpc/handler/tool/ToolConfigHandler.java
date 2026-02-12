package com.jaguarliu.ai.gateway.rpc.handler.tool;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.tools.ToolConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 工具配置获取 — tools.config.get
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ToolConfigGetHandler implements RpcHandler {

    private final ToolConfigService toolConfigService;

    @Override
    public String getMethod() {
        return "tools.config.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
                RpcResponse.success(request.getId(), toolConfigService.getConfig())
        );
    }
}

/**
 * 工具配置保存 — tools.config.save
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ToolConfigSaveHandler implements RpcHandler {

    private final ToolConfigService toolConfigService;

    @Override
    public String getMethod() {
        return "tools.config.save";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = extractParams(request.getPayload());
            toolConfigService.saveConfig(params);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to save tool config", e);
            return Mono.just(RpcResponse.error(request.getId(), "SAVE_FAILED", e.getMessage()));
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        throw new IllegalArgumentException("Invalid payload format");
    }
}
