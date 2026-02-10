package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SSH Connector 超时机制测试
 * 注意：这些是集成测试，需要真实的 SSH 服务器才能运行
 */
class SshConnectorTimeoutTest {

    @Test
    @Disabled("Requires real SSH server - run manually with integration tests")
    void testExecutionHardTimeout() {
        // 使用 mock SSH server 或跳过
        // 测试命令执行超过 execTimeout 时能正确中断并返回超时错误
        SshConnector connector = new SshConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(2)
                .maxOutputBytes(32000)
                .build();

        long startTime = System.currentTimeMillis();
        ExecResult result = connector.execute("password", node, "sleep 100", options);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isTimedOut(), "Result should indicate timeout");
        assertTrue(duration < 5000, "Should timeout within ~2 seconds (with margin)");
    }

    @Test
    @Disabled("Requires network access - run manually with integration tests")
    void testConnectionTimeoutSeparate() {
        SshConnector connector = new SshConnector();
        NodeEntity node = new NodeEntity();
        node.setHost("192.0.2.1"); // TEST-NET-1, non-routable
        node.setPort(22);
        node.setUsername("test");

        boolean connected = connector.testConnection("password", node);
        assertFalse(connected, "Should fail to connect to non-routable address");
    }

    @Test
    void testExecutorServiceConfiguration() {
        // 单元测试：验证 SshConnector 正确初始化（不需要真实连接）
        SshConnector connector = new SshConnector();
        assertNotNull(connector, "SshConnector should be instantiated");
        assertEquals("ssh", connector.getType(), "Connector type should be 'ssh'");
    }

    private NodeEntity createTestNode() {
        NodeEntity node = new NodeEntity();
        node.setHost("localhost");
        node.setPort(22);
        node.setUsername("testuser");
        node.setAuthType("password");
        return node;
    }
}
