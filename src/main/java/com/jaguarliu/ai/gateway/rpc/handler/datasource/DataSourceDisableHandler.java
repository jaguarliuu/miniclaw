package com.jaguarliu.ai.gateway.rpc.handler.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * datasources.disable - 禁用数据源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceDisableHandler implements RpcHandler {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "datasources.disable";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String id = (String) params.get("id");

            if (id == null || id.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");
            }

            dataSourceService.disableDataSource(id);
            return RpcResponse.success(request.getId(), Map.of("disabled", true));

        }).onErrorResume(e -> {
            log.error("Failed to disable data source: {}", e.getMessage(), e);

            if (e instanceof IllegalArgumentException) {
                return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", e.getMessage()));
            }

            return Mono.just(RpcResponse.error(request.getId(), "DISABLE_FAILED",
                    "Failed to disable data source: " + e.getMessage()));
        });
    }
}
