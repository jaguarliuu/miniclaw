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
 * 测试 MCP Server 连接
 * RPC Method: mcp.servers.test
 *
 * 用于前端配置验证，不会保存配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerTestHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public String getMethod() {
        return "mcp.servers.test";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        log.info("Testing MCP server connection");

        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) request.getPayload();

            // 构建临时配置
            var config = new McpProperties.ServerConfig();
            config.setName((String) payload.get("name"));
            config.setTransport(McpProperties.TransportType.valueOf((String) payload.get("transportType")));
            config.setCommand((String) payload.getOrDefault("command", null));
            config.setArgs((List<String>) payload.getOrDefault("args", List.of()));
            config.setUrl((String) payload.getOrDefault("url", null));
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

            config.setRequestTimeoutSeconds((Integer) payload.getOrDefault("requestTimeoutSeconds", 30));

            try {
                // 测试连接（阻塞操作）
                boolean success = mcpServerService.testConnection(config);

                return RpcResponse.success(request.getId(), Map.of(
                        "success", success,
                        "message", success ? "Connection successful" : "Connection failed"
                ));
            } catch (Exception e) {
                log.error("Connection test failed", e);
                return RpcResponse.success(request.getId(), Map.of(
                        "success", false,
                        "message", "Connection test failed: " + e.getMessage()
                ));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
