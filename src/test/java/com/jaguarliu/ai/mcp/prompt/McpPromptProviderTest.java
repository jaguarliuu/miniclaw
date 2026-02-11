package com.jaguarliu.ai.mcp.prompt;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpPromptProviderTest {

    @Autowired
    private McpPromptProvider promptProvider;

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
    void shouldLoadPromptsFromMcpServers() {
        String prompts = promptProvider.getSystemPromptAdditions();
        assertThat(prompts).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenNoClientsAvailable() {
        // Given: No MCP servers configured
        assertThat(mcpClientManager.getAllClients()).isEmpty();

        // When: Get system prompt additions
        String prompts = promptProvider.getSystemPromptAdditions();

        // Then: Should return empty string
        assertThat(prompts).isEmpty();
    }

    @Test
    void shouldHandleConnectionErrors() {
        // Given: No connected clients
        // When: Get prompts
        String prompts = promptProvider.getSystemPromptAdditions();

        // Then: Should not throw exception
        assertThat(prompts).isNotNull();
    }
}
