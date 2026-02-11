package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpToolRegistry integration test
 * Tests the automatic tool discovery and registration
 */
@SpringBootTest
class McpToolRegistryTest {

    @Autowired(required = false)
    private McpToolRegistry mcpToolRegistry;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private McpClientManager mcpClientManager;

    @Autowired
    private McpServerRepository repository;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        repository.deleteAll();
    }

    @Test
    void shouldInitializeWithoutClients() {
        // Given: McpToolRegistry exists
        assertThat(mcpToolRegistry).isNotNull();

        // When: No MCP clients connected
        // Then: Should initialize without errors
        assertThat(mcpClientManager.getAllClients()).isEmpty();
    }

    @Test
    void shouldRefreshToolsWithoutErrors() {
        // Given: Registry is initialized
        assertThat(mcpToolRegistry).isNotNull();

        // When: Refresh tools
        mcpToolRegistry.refreshTools();

        // Then: Should complete without errors (no tools registered since no clients)
        assertThat(true).isTrue();
    }
}
