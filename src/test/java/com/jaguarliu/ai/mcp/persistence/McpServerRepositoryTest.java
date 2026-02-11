package com.jaguarliu.ai.mcp.persistence;

import com.jaguarliu.ai.mcp.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class McpServerRepositoryTest {

    @Autowired
    private McpServerRepository repository;

    @Test
    void shouldSaveAndLoadMcpServerConfig() {
        var entity = new McpServerEntity();
        entity.setName("test-server");
        entity.setTransportType(McpProperties.TransportType.STDIO);
        entity.setCommand("npx");
        entity.setArgs(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));
        entity.setEnabled(true);
        entity.setToolPrefix("test_");

        var saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("test-server");
    }

    @Test
    void shouldFindEnabledServers() {
        var entity1 = createEntity("server1", true);
        var entity2 = createEntity("server2", false);

        repository.save(entity1);
        repository.save(entity2);

        var enabled = repository.findByEnabledTrue();

        assertThat(enabled).hasSize(1);
        assertThat(enabled.get(0).getName()).isEqualTo("server1");
    }

    private McpServerEntity createEntity(String name, boolean enabled) {
        var entity = new McpServerEntity();
        entity.setName(name);
        entity.setTransportType(McpProperties.TransportType.STDIO);
        entity.setCommand("test");
        entity.setEnabled(enabled);
        return entity;
    }
}
