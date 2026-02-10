package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeValidationTest {

    private final NodeValidator validator = new NodeValidator();

    @Test
    void testSshNodeValidConfiguration() {
        // 正常配置应该通过
        assertDoesNotThrow(() ->
            validator.validate("ssh", "192.168.1.100", 22, "admin")
        );
    }

    @Test
    void testSshNodeMissingHost() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", null, 22, "admin")
        );
        assertTrue(ex.getMessage().contains("host is required"));
    }

    @Test
    void testSshNodeLocalhostBlocked() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "localhost", 22, "admin")
        );
        assertTrue(ex.getMessage().contains("cannot be localhost"));
        assertTrue(ex.getMessage().contains("security risk"));
    }

    @Test
    void testSshNode127001Blocked() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "127.0.0.1", 22, "admin")
        );
        assertTrue(ex.getMessage().contains("cannot be localhost"));
    }

    @Test
    void testSshNodeRootUserBlocked() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "192.168.1.100", 22, "root")
        );
        assertTrue(ex.getMessage().contains("cannot be 'root'"));
        assertTrue(ex.getMessage().contains("use sudo"));
    }

    @Test
    void testSshNodeMissingUsername() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "192.168.1.100", 22, null)
        );
        assertTrue(ex.getMessage().contains("username is required"));
    }

    @Test
    void testSshNodeMissingPort() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "192.168.1.100", null, "admin")
        );
        assertTrue(ex.getMessage().contains("port is required"));
    }

    @Test
    void testSshNodeInvalidPort() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "192.168.1.100", 70000, "admin")
        );
        assertTrue(ex.getMessage().contains("port must be between"));
    }

    @Test
    void testMultipleErrors() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("ssh", "localhost", null, "root")
        );
        String message = ex.getMessage();
        assertTrue(message.contains("cannot be localhost"));
        assertTrue(message.contains("port is required"));
        assertTrue(message.contains("cannot be 'root'"));
    }

    @Test
    void testK8sNodeValidConfiguration() {
        // K8s 节点 host/port 可选
        assertDoesNotThrow(() ->
            validator.validate("k8s", null, null, null)
        );

        assertDoesNotThrow(() ->
            validator.validate("k8s", "k8s.example.com", 6443, null)
        );
    }

    @Test
    void testK8sNodeLocalhostBlocked() {
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
            validator.validate("k8s", "localhost", 6443, null)
        );
        assertTrue(ex.getMessage().contains("cannot be localhost"));
    }
}
