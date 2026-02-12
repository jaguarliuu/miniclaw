package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool Adapter
 * Adapts MCP Tool to miniclaw's Tool interface
 */
@Slf4j
@RequiredArgsConstructor
public class McpToolAdapter implements Tool {

    private final McpSchema.Tool mcpTool;
    private final ManagedMcpClient mcpClient;
    private final ToolDefinition toolDefinition;

    /**
     * Constructor to create adapter from MCP Tool
     */
    public McpToolAdapter(
            McpSchema.Tool mcpTool,
            ManagedMcpClient mcpClient
    ) {
        this.mcpTool = mcpTool;
        this.mcpClient = mcpClient;
        this.toolDefinition = convertToToolDefinition(mcpTool, mcpClient);
    }

    /**
     * Convert MCP Tool to ToolDefinition
     */
    private static ToolDefinition convertToToolDefinition(
            McpSchema.Tool mcpTool,
            ManagedMcpClient mcpClient
    ) {
        String toolName = mcpClient.getToolPrefix() + mcpTool.name();
        String description = mcpTool.description() != null
                ? mcpTool.description()
                : "MCP Tool: " + mcpTool.name();

        // Convert MCP inputSchema to ToolDefinition parameters
        // JsonSchema is internally a Map structure
        Map<String, Object> parameters = null;
        if (mcpTool.inputSchema() != null) {
            try {
                // JsonSchema can be converted to Map using Jackson
                parameters = convertJsonSchemaToMap(mcpTool.inputSchema());
            } catch (Exception e) {
                log.warn("Failed to convert inputSchema for tool: {}", mcpTool.name(), e);
                parameters = Map.of("type", "object", "properties", Map.of());
            }
        }

        if (parameters == null) {
            parameters = Map.of("type", "object", "properties", Map.of());
        }

        // Check if HITL is required
        boolean requiresHitl = mcpClient.getConfig().isRequiresHitl()
                || (mcpClient.getConfig().getHitlTools() != null
                    && mcpClient.getConfig().getHitlTools().contains(mcpTool.name()));

        return ToolDefinition.builder()
                .name(toolName)
                .description(description + " (from MCP server: " + mcpClient.getName() + ")")
                .parameters(parameters)
                .hitl(requiresHitl)
                .build();
    }

    /**
     * Convert JsonSchema to Map (assuming JsonSchema wraps a Map internally)
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertJsonSchemaToMap(McpSchema.JsonSchema jsonSchema) {
        // JsonSchema record contains the schema as internal fields
        // For now, we'll create a basic object schema
        // TODO: Proper JsonSchema to Map conversion when SDK provides accessor methods
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public ToolDefinition getDefinition() {
        return toolDefinition;
    }

    @Override
    public String getMcpServerName() {
        return mcpClient.getName();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        log.info("Executing MCP tool: {} with arguments: {}", mcpTool.name(), arguments);

        if (!mcpClient.isConnected()) {
            log.error("MCP client not connected: {}", mcpClient.getName());
            return Mono.just(ToolResult.error(
                    "MCP server not connected: " + mcpClient.getName()
            ));
        }

        return Mono.fromCallable(() -> {
            try {
                // Create CallToolRequest
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                        mcpTool.name(),
                        arguments,
                        null // _meta
                );

                // Call MCP tool using sync client
                McpSchema.CallToolResult result = mcpClient.getClient().callTool(request);

                // Check if there's an error
                if (result.isError() != null && result.isError()) {
                    log.error("MCP tool returned error: {}", result);
                    return ToolResult.error(extractErrorMessage(result));
                }

                // Extract success result
                String content = extractContent(result);
                return ToolResult.success(content);

            } catch (Exception e) {
                log.error("MCP tool execution failed: {}", mcpTool.name(), e);
                return ToolResult.error("MCP tool execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * Extract content from CallToolResult
     */
    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            return result.content().stream()
                    .map(content -> {
                        // Extract text based on Content type
                        if (content instanceof McpSchema.TextContent textContent) {
                            return textContent.text();
                        }
                        return content.toString();
                    })
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        return "";
    }

    /**
     * Extract error message from CallToolResult
     */
    private String extractErrorMessage(McpSchema.CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            return extractContent(result);
        }
        return "Unknown error from MCP tool";
    }
}
