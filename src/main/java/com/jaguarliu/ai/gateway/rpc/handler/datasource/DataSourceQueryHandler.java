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
 * datasources.query - 执行查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceQueryHandler implements RpcHandler {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "datasources.query";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String id = (String) params.get("id");
            String query = (String) params.get("query");
            Integer maxRows = params.get("maxRows") != null
                    ? ((Number) params.get("maxRows")).intValue() : null;
            Integer timeoutSeconds = params.get("timeoutSeconds") != null
                    ? ((Number) params.get("timeoutSeconds")).intValue() : null;

            if (id == null || id.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");
            }
            if (query == null || query.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "query is required");
            }

            var queryResult = dataSourceService.executeQuery(id, query, maxRows, timeoutSeconds);
            return RpcResponse.success(request.getId(), queryResult);

        }).onErrorResume(e -> {
            log.error("Failed to execute query: {}", e.getMessage(), e);

            if (e instanceof IllegalArgumentException) {
                return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", e.getMessage()));
            }
            if (e instanceof IllegalStateException) {
                return Mono.just(RpcResponse.error(request.getId(), "DATASOURCE_UNAVAILABLE", e.getMessage()));
            }

            return Mono.just(RpcResponse.error(request.getId(), "QUERY_FAILED",
                    "Failed to execute query: " + e.getMessage()));
        });
    }
}
