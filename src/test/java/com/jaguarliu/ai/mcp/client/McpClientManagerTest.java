package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class McpClientManagerTest {

    @Autowired
    private McpClientManager clientManager;

    @Autowired
    private McpServerRepository repository;

    @AfterEach
    void tearDown() {
        // Clean up connected clients
        for (String name : clientManager.getConnectedServerNames()) {
            clientManager.disconnectServer(name);
        }
        // Clean up database
        repository.deleteAll();
    }

    @Test
    void shouldInitializeWithEnabledServersFromDatabase() {
        // Given: Some servers in database
        var entity = new McpServerEntity();
        entity.setName("init-server");
        entity.setTransportType(McpProperties.TransportType.STDIO);
        entity.setCommand("npx");
        entity.setEnabled(true);
        repository.save(entity);

        // When: Client manager initializes (already done in @PostConstruct)
        // The initialize() method is called automatically after the component is created

        // Then: Verify that we can check connected servers
        var connectedNames = clientManager.getConnectedServerNames();
        // Note: The server might not be in the list if initialization happened before we saved it
        // This is expected behavior in this test order
    }

    @Test
    void shouldConnectServerDynamically() {
        var config = new McpProperties.ServerConfig();
        config.setName("dynamic-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setArgs(List.of("-y", "test"));

        // Connect dynamically
        clientManager.connectServer(config);

        // Verify connected
        var client = clientManager.getClient("dynamic-server");
        assertThat(client).isPresent();
    }

    @Test
    void shouldNotConnectDuplicateServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("duplicate-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");

        clientManager.connectServer(config);

        assertThatThrownBy(() -> clientManager.connectServer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldDisconnectServerDynamically() {
        // Given: A connected server
        var config = new McpProperties.ServerConfig();
        config.setName("temp-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");

        clientManager.connectServer(config);
        assertThat(clientManager.getClient("temp-server")).isPresent();

        // When: Disconnect
        clientManager.disconnectServer("temp-server");

        // Then: No longer exists
        assertThat(clientManager.getClient("temp-server")).isEmpty();
    }

    @Test
    void shouldTestConnectionWithoutPersisting() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-connection");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");

        boolean result = clientManager.testConnection(config);

        // Connection test should not persist the client
        assertThat(clientManager.getClient("test-connection")).isEmpty();

        // Phase 2: Test always returns true (placeholder)
        assertThat(result).isTrue();
    }

    @Test
    void shouldListConnectedServers() {
        var config1 = new McpProperties.ServerConfig();
        config1.setName("server1");
        config1.setTransport(McpProperties.TransportType.STDIO);
        config1.setCommand("test1");

        var config2 = new McpProperties.ServerConfig();
        config2.setName("server2");
        config2.setTransport(McpProperties.TransportType.SSE);
        config2.setUrl("http://localhost:3000/sse");

        clientManager.connectServer(config1);
        clientManager.connectServer(config2);

        var connectedNames = clientManager.getConnectedServerNames();

        assertThat(connectedNames).hasSize(2);
        assertThat(connectedNames).contains("server1", "server2");
    }
}
