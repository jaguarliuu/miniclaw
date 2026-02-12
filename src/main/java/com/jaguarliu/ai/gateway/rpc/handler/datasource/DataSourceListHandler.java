package com.jaguarliu.ai.gateway.rpc.handler.datasource;

import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * datasources.list - 列出所有数据源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceListHandler implements RpcHandler {

    private final DataSourceService dataSourceService;

    @Override
    public String getMethod() {
        return "datasources.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            var dataSources = dataSourceService.listDataSources();
            return RpcResponse.success(request.getId(), dataSources);
        }).onErrorResume(e -> {
            log.error("Failed to list data sources: {}", e.getMessage(), e);
            return Mono.just(RpcResponse.error(request.getId(), "LIST_FAILED",
                    "Failed to list data sources: " + e.getMessage()));
        });
    }
}
