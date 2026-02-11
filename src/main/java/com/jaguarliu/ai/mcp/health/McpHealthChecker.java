package com.jaguarliu.ai.mcp.health;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MCP 健康检查器
 * 定期检查 MCP 客户端连接状态并尝试重连
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpHealthChecker {

    private final McpClientManager mcpClientManager;
    private final McpProperties mcpProperties;

    /**
     * 定期健康检查
     * 根据配置的 interval-seconds 执行
     */
    @Scheduled(
            fixedDelayString = "${mcp.health-check.interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS,
            initialDelay = 30
    )
    public void checkHealth() {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            return;
        }

        log.debug("Running MCP health check for {} clients", clients.size());

        for (ManagedMcpClient client : clients) {
            checkClientHealth(client);
        }
    }

    /**
     * 检查单个客户端健康状态
     */
    private void checkClientHealth(ManagedMcpClient client) {
        try {
            // 发送 ping 请求测试连接
            if (client.isConnected()) {
                client.getClient().ping();
                log.debug("MCP client healthy: {}", client.getName());
            } else {
                log.warn("MCP client disconnected: {}", client.getName());
                attemptReconnect(client);
            }
        } catch (Exception e) {
            log.error("MCP health check failed for: {}", client.getName(), e);
            client.markDisconnected();
            attemptReconnect(client);
        }
    }

    /**
     * 尝试重新连接
     */
    private void attemptReconnect(ManagedMcpClient client) {
        log.info("Attempting to reconnect MCP client: {}", client.getName());

        try {
            client.initialize();
            log.info("Successfully reconnected MCP client: {}", client.getName());
        } catch (Exception e) {
            log.error("Failed to reconnect MCP client: {}", client.getName(), e);
        }
    }
}
