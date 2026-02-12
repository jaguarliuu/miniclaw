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
 * datasources.testNew - 测试新数据源连接（不保存）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceTestNewHandler implements RpcHandler {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "datasources.testNew";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            CreateDataSourceRequest createRequest = objectMapper.convertValue(
                    request.getPayload(), CreateDataSourceRequest.class);

            var testResult = dataSourceService.testConnection(createRequest);
            return RpcResponse.success(request.getId(), testResult);

        }).onErrorResume(e -> {
            log.error("Failed to test new data source connection: {}", e.getMessage(), e);

            return Mono.just(RpcResponse.error(request.getId(), "TEST_FAILED",
                    "Failed to test connection: " + e.getMessage()));
        });
    }
}
