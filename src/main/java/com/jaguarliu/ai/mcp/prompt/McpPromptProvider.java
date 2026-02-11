package com.jaguarliu.ai.mcp.prompt;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
