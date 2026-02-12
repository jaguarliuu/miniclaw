package com.jaguarliu.ai.gateway.rpc.handler.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.datasource.application.dto.CreateDataSourceRequest;
import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * datasources.create - 创建数据源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceCreateHandler implements RpcHandler {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "datasources.create";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            CreateDataSourceRequest createRequest = objectMapper.convertValue(
                    request.getPayload(), CreateDataSourceRequest.class);

            var dataSource = dataSourceService.createDataSource(createRequest);
            return RpcResponse.success(request.getId(), dataSource);

        }).onErrorResume(e -> {
            log.error("Failed to create data source: {}", e.getMessage(), e);

            if (e instanceof IllegalArgumentException) {
                return Mono.just(RpcResponse.error(request.getId(), "INVALID_ARGUMENT", e.getMessage()));
            }

            return Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED",
                    "Failed to create data source: " + e.getMessage()));
        });
    }
}
