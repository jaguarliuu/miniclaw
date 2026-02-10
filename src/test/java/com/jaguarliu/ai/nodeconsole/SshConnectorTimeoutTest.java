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
        ExecResult result = connector.execute("password", node, "sleep 100", 2, 32000);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isTimedOut(), "Result should indicate timeout");
        assertTrue(duration < 5000, "Should timeout within ~2 seconds (with margin)");
    }

    @Test
    void testConnectionTimeoutSeparate() {
        SshConnector connector = new SshConnector();
        NodeEntity node = new NodeEntity();
        node.setHost("192.0.2.1"); // TEST-NET-1, non-routable
        node.setPort(22);
        node.setUsername("test");

        boolean connected = connector.testConnection("password", node);
        assertFalse(connected, "Should fail to connect to non-routable address");
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
