package com.jaguarliu.ai.mcp.prompt;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * MCP Prompt Provider
 * Fetches prompts from MCP servers and integrates them into system prompts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpPromptProvider {

    private final McpClientManager mcpClientManager;

    /**
     * Get system prompt additions from all MCP servers
     *
     * @return Combined system prompt additions
     */
    public String getSystemPromptAdditions() {
        return getSystemPromptAdditions(null);
    }

    /**
     * Get system prompt additions from MCP servers (with optional exclusion)
     *
     * @param excludedServers MCP server names to exclude (null means no exclusion)
     * @return Combined system prompt additions
     */
    public String getSystemPromptAdditions(Set<String> excludedServers) {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## MCP Server Capabilities\n\n");

        for (ManagedMcpClient client : clients) {
            if (!client.isConnected()) {
                continue;
            }

            // Skip excluded servers
            if (excludedServers != null && excludedServers.contains(client.getName())) {
                continue;
            }

            try {
                String serverPrompts = getPromptsFromServer(client);
                if (!serverPrompts.isEmpty()) {
                    sb.append(serverPrompts).append("\n");
                }
            } catch (Exception e) {
                log.error("Failed to get prompts from MCP server: {}", client.getName(), e);
            }
        }

        String result = sb.toString();
        // If only header was added, return empty string
        if (result.equals("\n\n## MCP Server Capabilities\n\n")) {
            return "";
        }

        return result;
    }

    /**
     * Get prompts from a single MCP server
     */
    private String getPromptsFromServer(ManagedMcpClient client) {
        try {
            // 检查 server 是否声明了 prompts 能力
            // 如果 server 不支持 prompts，直接跳过避免 SDK 层报 ERROR
            McpSchema.ServerCapabilities capabilities = client.getServerCapabilities();
            if (capabilities == null || capabilities.prompts() == null) {
                return "";
            }

            McpSchema.ListPromptsResult listPromptsResult = client.getClient().listPrompts();

            if (listPromptsResult.prompts() == null || listPromptsResult.prompts().isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("### %s\n\n", client.getName()));

            for (McpSchema.Prompt prompt : listPromptsResult.prompts()) {
                sb.append(String.format("- **%s**: %s\n",
                        prompt.name(),
                        prompt.description() != null ? prompt.description() : "No description"
                ));
            }

            return sb.toString();

        } catch (Exception e) {
            log.debug("MCP server does not support prompts or failed to list: {}", client.getName());
            return "";
        }
    }
}
