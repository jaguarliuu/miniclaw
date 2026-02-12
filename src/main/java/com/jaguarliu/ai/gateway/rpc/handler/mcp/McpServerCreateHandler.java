package com.jaguarliu.ai.gateway.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 创建新的 MCP Server 配置
 * RPC Method: mcp.servers.create
 *
 * Payload:
 * {
 *   "name": "my-server",
 *   "transportType": "STDIO",
 *   "command": "npx",
 *   "args": ["-y", "..."],
 *   "url": "http://...",  // for SSE/HTTP
 *   "enabled": true,
 *   "toolPrefix": "prefix_"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerCreateHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public String getMethod() {
        return "mcp.servers.create";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        log.info("Creating new MCP server");

        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) request.getPayload();

            // 构建配置
            var config = new McpProperties.ServerConfig();
            config.setName((String) payload.get("name"));
            config.setTransport(McpProperties.TransportType.valueOf((String) payload.get("transportType")));
            config.setCommand((String) payload.getOrDefault("command", null));
            config.setArgs((List<String>) payload.getOrDefault("args", List.of()));
            config.setWorkingDir((String) payload.getOrDefault("workingDir", null));

            // 处理环境变量：去除 = 号周围的空格
            List<String> envVars = (List<String>) payload.getOrDefault("env", List.of());
            config.setEnv(envVars.stream()
                    .map(String::trim)
                    .map(env -> {
                        // 去除 = 号周围的空格
                        int idx = env.indexOf('=');
                        if (idx > 0 && idx < env.length() - 1) {
                            String key = env.substring(0, idx).trim();
                            String value = env.substring(idx + 1).trim();
                            return key + "=" + value;
                        }
                        return env;
                    })
                    .collect(java.util.stream.Collectors.toList()));

            config.setUrl((String) payload.getOrDefault("url", null));
            config.setEnabled((Boolean) payload.getOrDefault("enabled", true));
            config.setToolPrefix((String) payload.getOrDefault("toolPrefix", ""));
            config.setRequiresHitl((Boolean) payload.getOrDefault("requiresHitl", false));
            config.setHitlTools((List<String>) payload.getOrDefault("hitlTools", List.of()));
            config.setRequestTimeoutSeconds((Integer) payload.getOrDefault("requestTimeoutSeconds", 30));

            try {
                // 创建服务器（包含阻塞的 MCP 连接初始化）
                var entity = mcpServerService.createServer(config);

                return RpcResponse.success(request.getId(), Map.of(
                        "server", Map.of(
                                "id", entity.getId(),
                                "name", entity.getName(),
                                "enabled", entity.getEnabled()
                        )
                ));
            } catch (Exception e) {
                log.error("Failed to create MCP server", e);
                return RpcResponse.error(request.getId(), "MCP_CREATE_FAILED",
                        "Failed to create MCP server: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
