package com.jaguarliu.ai.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列出所有 MCP Server 配置
 * RPC Method: mcp.servers.list
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerListHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public String getMethod() {
        return "mcp.servers.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        log.debug("Listing all MCP servers");

        try {
            var servers = mcpServerService.listServers();

            var result = servers.stream()
                    .map(entity -> Map.of(
                            "id", entity.getId(),
                            "name", entity.getName(),
                            "transportType", entity.getTransportType().toString(),
                            "enabled", entity.getEnabled(),
                            "toolPrefix", entity.getToolPrefix(),
                            "url", entity.getUrl() != null ? entity.getUrl() : "",
                            "command", entity.getCommand() != null ? entity.getCommand() : "",
                            "createdAt", entity.getCreatedAt().toString(),
                            "updatedAt", entity.getUpdatedAt().toString()
                    ))
                    .collect(Collectors.toList());

            return Mono.just(RpcResponse.success(request.getId(), Map.of("servers", result)));

        } catch (Exception e) {
            log.error("Failed to list MCP servers", e);
            return Mono.just(RpcResponse.error(request.getId(), "MCP_LIST_FAILED", "Failed to list MCP servers: " + e.getMessage()));
        }
    }
}
