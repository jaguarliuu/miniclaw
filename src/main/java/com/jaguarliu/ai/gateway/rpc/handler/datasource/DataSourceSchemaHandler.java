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
 * datasources.schema - 获取数据源 Schema 元数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceSchemaHandler implements RpcHandler {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "datasources.schema";
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

            var schemaMetadata = dataSourceService.getSchemaMetadata(id);
            return RpcResponse.success(request.getId(), schemaMetadata);

        }).onErrorResume(e -> {
            log.error("Failed to get schema metadata: {}", e.getMessage(), e);

            if (e instanceof IllegalArgumentException) {
                return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", e.getMessage()));
            }
            if (e instanceof IllegalStateException) {
                return Mono.just(RpcResponse.error(request.getId(), "DATASOURCE_UNAVAILABLE", e.getMessage()));
            }

            return Mono.just(RpcResponse.error(request.getId(), "SCHEMA_FAILED",
                    "Failed to get schema metadata: " + e.getMessage()));
        });
    }
}
