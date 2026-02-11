package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Tool Registry
 * Automatically discovers MCP client tools and registers them to ToolRegistry
 *
 * Uses SmartInitializingSingleton to ensure registration happens after ToolRegistry is initialized
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry implements SmartInitializingSingleton {

    private final McpClientManager mcpClientManager;
    private final ToolRegistry toolRegistry;

    /**
     * Called after all singleton beans are fully initialized
     * Discovers and registers MCP tools
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("Discovering and registering MCP tools");
        refreshTools();
    }

    /**
     * Refresh MCP tool registration
     * Discovers tools from all connected MCP clients and registers them
     */
    public void refreshTools() {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            log.info("No MCP clients available for tool discovery");
            return;
        }

        int totalTools = 0;
        for (ManagedMcpClient client : clients) {
            if (!client.isConnected()) {
                log.warn("MCP client not connected, skipping: {}", client.getName());
                continue;
            }

            try {
                int count = discoverAndRegisterTools(client);
                totalTools += count;
            } catch (Exception e) {
                log.error("Failed to discover tools from MCP client: {}", client.getName(), e);
            }
        }

        log.info("MCP tool discovery complete. Registered {} tools from {} clients",
                totalTools, clients.size());
    }

    /**
     * Discover and register tools from a single MCP client
     */
    private int discoverAndRegisterTools(ManagedMcpClient client) {
        log.info("Discovering tools from MCP server: {}", client.getName());

        int count = 0;

        try {
            // List and register tools
            McpSchema.ListToolsResult listToolsResult = client.getClient().listTools();

            if (listToolsResult.tools() != null && !listToolsResult.tools().isEmpty()) {
                for (McpSchema.Tool mcpTool : listToolsResult.tools()) {
                    var adapter = new McpToolAdapter(mcpTool, client);
                    toolRegistry.register(adapter);
                    log.debug("Registered MCP tool: {}", adapter.getDefinition().getName());
                    count++;
                }
            }

            // TODO Phase 4: Check if resources are supported and register McpResourceTool
            // var listResourcesResult = client.getClient().listResources();
            // if (listResourcesResult.resources() != null && !listResourcesResult.resources().isEmpty()) {
            //     var resourceTool = new McpResourceTool(client);
            //     toolRegistry.register(resourceTool);
            //     count++;
            // }

            log.info("Registered {} tools from MCP server: {}", count, client.getName());
            return count;

        } catch (Exception e) {
            log.error("Failed to list tools from MCP server: {}", client.getName(), e);
            throw new RuntimeException("Failed to discover tools from: " + client.getName(), e);
        }
    }
}
