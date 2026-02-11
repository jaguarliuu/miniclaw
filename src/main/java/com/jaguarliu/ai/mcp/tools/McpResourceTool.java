package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP Resource Access Tool
 * Provides read access to MCP Resources (files, data, etc.)
 */
@Slf4j
@RequiredArgsConstructor
public class McpResourceTool implements Tool {

    private final ManagedMcpClient mcpClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name(mcpClient.getToolPrefix() + "mcp_read_resource")
                .description(String.format(
                        "Read a resource from MCP server '%s'. " +
                        "Resources provide access to data like files, database records, API responses, etc.",
                        mcpClient.getName()
                ))
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "uri", Map.of(
                                        "type", "string",
                                        "description", "URI of the resource to read (e.g., 'file:///path/to/file.txt')"
                                )
                        ),
                        "required", new String[]{"uri"}
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String uri = (String) arguments.get("uri");

        if (uri == null || uri.isBlank()) {
            return Mono.just(ToolResult.error("Resource URI is required"));
        }

        if (!mcpClient.isConnected()) {
            return Mono.just(ToolResult.error(
                    "MCP server not connected: " + mcpClient.getName()
            ));
        }

        log.info("Reading MCP resource: {} from server: {}", uri, mcpClient.getName());

        return Mono.fromCallable(() -> {
            try {
                // Create read resource request
                McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(
                        uri,
                        null // _meta
                );

                // Read resource from MCP server
                McpSchema.ReadResourceResult result = mcpClient.getClient().readResource(request);

                if (result.contents() == null || result.contents().isEmpty()) {
                    return ToolResult.success("(empty resource)");
                }

                // Combine all content
                String content = result.contents().stream()
                        .map(resourceContent -> {
                            // Extract text from ResourceContents
                            if (resourceContent instanceof McpSchema.TextResourceContents textContent) {
                                return textContent.text();
                            } else if (resourceContent instanceof McpSchema.BlobResourceContents blobContent) {
                                // For blob content, return a description
                                return String.format("[Binary data: %s, mime-type: %s]",
                                        blobContent.uri(), blobContent.mimeType());
                            }
                            return resourceContent.toString();
                        })
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("(empty)");

                return ToolResult.success(content);

            } catch (Exception e) {
                log.error("Failed to read MCP resource: {}", uri, e);
                return ToolResult.error("Failed to read resource: " + e.getMessage());
            }
        });
    }
}
