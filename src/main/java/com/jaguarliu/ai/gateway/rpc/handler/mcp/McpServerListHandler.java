package com.jaguarliu.ai.gateway.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
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
    private final McpClientManager mcpClientManager;

    @Override
    public String getMethod() {
        return "mcp.servers.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        log.debug("Listing all MCP servers");

        return Mono.fromCallable(() -> {
            var servers = mcpServerService.listServers();

            var result = servers.stream()
                    .map(entity -> {
                        Map<String, Object> serverMap = new HashMap<>();
                        serverMap.put("id", entity.getId());
                        serverMap.put("name", entity.getName());
                        serverMap.put("transportType", entity.getTransportType().toString());
                        serverMap.put("enabled", entity.getEnabled());
                        serverMap.put("toolPrefix", entity.getToolPrefix());
                        serverMap.put("url", entity.getUrl() != null ? entity.getUrl() : "");
                        serverMap.put("command", entity.getCommand() != null ? entity.getCommand() : "");
                        serverMap.put("createdAt", entity.getCreatedAt().toString());
                        serverMap.put("updatedAt", entity.getUpdatedAt().toString());

                        // 获取工具数量（阻塞操作）
                        int toolCount = getToolCount(entity.getName());
                        serverMap.put("toolCount", toolCount);

                        return serverMap;
                    })
                    .collect(Collectors.toList());

            return RpcResponse.success(request.getId(), Map.of("servers", result));

        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("Failed to list MCP servers", e);
              return Mono.just(RpcResponse.error(request.getId(), "MCP_LIST_FAILED", "Failed to list MCP servers: " + e.getMessage()));
          });
    }

    /**
     * 获取 MCP 服务器的工具数量
     */
    private int getToolCount(String serverName) {
        try {
            var clientOpt = mcpClientManager.getClient(serverName);
            if (clientOpt.isEmpty()) {
                log.debug("No client found for server: {}", serverName);
                return 0;
            }

            ManagedMcpClient client = clientOpt.get();
            if (!client.isConnected()) {
                log.debug("Client not connected for server: {}", serverName);
                return 0;
            }

            int count = 0;

            // 统计工具数量
            try {
                McpSchema.ListToolsResult result = client.getClient().listTools();
                if (result.tools() != null) {
                    count += result.tools().size();
                    log.debug("Server {} has {} tools", serverName, result.tools().size());
                }
            } catch (Exception e) {
                log.debug("Failed to list tools for {}: {}", serverName, e.getMessage());
            }

            // 统计资源工具（如果支持）
            try {
                McpSchema.ListResourcesResult result = client.getClient().listResources();
                if (result.resources() != null && !result.resources().isEmpty()) {
                    count += 1; // 资源作为一个工具
                    log.debug("Server {} has resources", serverName);
                }
            } catch (Exception e) {
                log.debug("Server {} does not support resources", serverName);
            }

            log.info("Server {} total tool count: {}", serverName, count);
            return count;
        } catch (Exception e) {
            log.warn("Failed to get tool count for {}: {}", serverName, e.getMessage());
            return 0;
        }
    }
}
