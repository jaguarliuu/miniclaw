package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

class ConnectorFactoryTest {

    @Test
    void testDuplicateConnectorTypeDetection() {
        // 创建两个相同类型的 connector
        Connector connector1 = new TestConnector("ssh");
        Connector connector2 = new TestConnector("ssh");

        ConnectorFactory factory = new ConnectorFactory(Arrays.asList(connector1, connector2));

        // 应该在 init 时抛出异常
        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::init);

        assertTrue(ex.getMessage().contains("Duplicate connector type"));
        assertTrue(ex.getMessage().contains("ssh"));
        assertTrue(ex.getMessage().contains("TestConnector"));
    }

    @Test
    void testUniqueConnectorTypesSucceed() {
        Connector connector1 = new TestConnector("ssh");
        Connector connector2 = new TestConnector("k8s");

        ConnectorFactory factory = new ConnectorFactory(Arrays.asList(connector1, connector2));

        // 不应该抛出异常
        assertDoesNotThrow(factory::init);

        // 应该能获取两个connector
        assertEquals(connector1, factory.get("ssh"));
        assertEquals(connector2, factory.get("k8s"));
    }

    @Test
    void testGetUnknownConnectorThrows() {
        ConnectorFactory factory = new ConnectorFactory(Arrays.asList(new TestConnector("ssh")));
        factory.init();

        assertThrows(IllegalArgumentException.class, () -> factory.get("unknown"));
    }

    @Test
    void testSupportsMethod() {
        ConnectorFactory factory = new ConnectorFactory(Arrays.asList(new TestConnector("ssh")));
        factory.init();

        assertTrue(factory.supports("ssh"));
        assertFalse(factory.supports("k8s"));
    }

    // 测试用 Connector 实现
    private static class TestConnector implements Connector {
        private final String type;

        TestConnector(String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public ExecResult execute(String credential, NodeEntity node, String command,
                                  int timeoutSeconds, int maxOutputBytes) {
            return new ExecResult.Builder().build();
        }

        @Override
        public boolean testConnection(String credential, NodeEntity node) {
            return true;
        }
    }
}
