package com.jaguarliu.ai.mcp.service;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class McpServerServiceTest {

    @Autowired
    private McpServerService service;

    @Autowired
    private McpServerRepository repository;

    @MockBean
    private McpClientManager clientManager;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        repository.deleteAll();

        // Configure mock to return true for testConnection
        when(clientManager.testConnection(any())).thenReturn(true);
    }

    @Test
    void shouldCreateAndConnectMcpServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setArgs(List.of("-y", "test"));
        config.setEnabled(true);

        var result = service.createServer(config);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test-server");

        // 验证已保存到数据库
        var found = repository.findByName("test-server");
        assertThat(found).isPresent();
    }

    @Test
    void shouldNotCreateDuplicateServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("duplicate");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("test");

        service.createServer(config);

        assertThatThrownBy(() -> service.createServer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldUpdateServer() {
        // Create initial server
        var config = new McpProperties.ServerConfig();
        config.setName("update-test");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setEnabled(true);

        var created = service.createServer(config);

        // Update server
        config.setCommand("python");
        config.setToolPrefix("py_");

        var updated = service.updateServer(created.getId(), config);

        assertThat(updated.getCommand()).isEqualTo("python");
        assertThat(updated.getToolPrefix()).isEqualTo("py_");
    }

    @Test
    void shouldDeleteServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("delete-test");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("test");

        var created = service.createServer(config);

        service.deleteServer(created.getId());

        var found = repository.findById(created.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void shouldListAllServers() {
        var config1 = new McpProperties.ServerConfig();
        config1.setName("server1");
        config1.setTransport(McpProperties.TransportType.STDIO);
        config1.setCommand("test1");

        var config2 = new McpProperties.ServerConfig();
        config2.setName("server2");
        config2.setTransport(McpProperties.TransportType.STDIO);
        config2.setCommand("test2");

        service.createServer(config1);
        service.createServer(config2);

        var servers = service.listServers();

        assertThat(servers).hasSize(2);
    }

    @Test
    void shouldListEnabledServersOnly() {
        var config1 = new McpProperties.ServerConfig();
        config1.setName("enabled-server");
        config1.setTransport(McpProperties.TransportType.STDIO);
        config1.setCommand("test1");
        config1.setEnabled(true);

        var config2 = new McpProperties.ServerConfig();
        config2.setName("disabled-server");
        config2.setTransport(McpProperties.TransportType.STDIO);
        config2.setCommand("test2");
        config2.setEnabled(false);

        service.createServer(config1);
        service.createServer(config2);

        var enabledServers = service.listEnabledServers();

        assertThat(enabledServers).hasSize(1);
        assertThat(enabledServers.get(0).getName()).isEqualTo("enabled-server");
    }

    @Test
    void shouldTestConnection() {
        var config = new McpProperties.ServerConfig();
        config.setName("connection-test");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");

        boolean result = service.testConnection(config);

        // With stub, should return true
        assertThat(result).isTrue();
    }
}
