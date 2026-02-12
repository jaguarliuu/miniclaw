package com.jaguarliu.ai.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 删除 MCP Server 配置
 * RPC Method: mcp.servers.delete
 *
 * Payload:
 * {
 *   "id": 123
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerDeleteHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public String getMethod() {
        return "mcp.servers.delete";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        log.info("Deleting MCP server");

        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) request.getPayload();

            // 获取 ID
            Long id = ((Number) payload.get("id")).longValue();

            try {
                // 删除服务器（包含阻塞的 MCP 断开连接操作）
                mcpServerService.deleteServer(id);

                return RpcResponse.success(request.getId(), Map.of(
                        "success", true,
                        "message", "MCP server deleted successfully"
                ));
            } catch (Exception e) {
                log.error("Failed to delete MCP server", e);
                return RpcResponse.error(request.getId(), "MCP_DELETE_FAILED",
                        "Failed to delete MCP server: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
