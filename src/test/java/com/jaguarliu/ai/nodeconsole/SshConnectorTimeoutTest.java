package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SSH Connector 超时机制测试
 */
class SshConnectorTimeoutTest {

    @Test
    void testExecutionHardTimeout() {
        // 使用 mock SSH server 或跳过
        // 测试命令执行超过 execTimeout 时能正确中断并返回超时错误
        SshConnector connector = new SshConnector();
        NodeEntity node = createTestNode();

        long startTime = System.currentTimeMillis();
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            // 执行一个会卡住的命令，超时时间设置为 2 秒
            connector.execute("password", node, "sleep 100", 2);
        });
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(exception.getMessage().contains("timeout") ||
                   exception.getMessage().contains("timed out"),
                   "Exception should indicate timeout");
        assertTrue(duration < 5000, "Should timeout within ~2 seconds (with margin)");
    }

    @Test
    void testConnectionTimeoutSeparate() {
        SshConnector connector = new SshConnector();
        NodeEntity node = new NodeEntity();
        node.setHost("192.0.2.1"); // TEST-NET-1, non-routable
        node.setPort(22);
        node.setUsername("test");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            connector.testConnection("password", node);
        });

        assertTrue(exception.getMessage().contains("timeout") ||
                   exception.getMessage().contains("connect"),
                   "Should fail with connection timeout");
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
