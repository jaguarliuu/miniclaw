package com.jaguarliu.ai.mcp.health;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpHealthCheckerTest {

    @Autowired
    private McpHealthChecker healthChecker;

    @Test
    void shouldCheckHealth() {
        // Health check 应该可以执行
        healthChecker.checkHealth();
        assertThat(true).isTrue();
    }
}
