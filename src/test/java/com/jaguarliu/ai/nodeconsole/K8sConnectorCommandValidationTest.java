package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * K8s Connector 命令验证测试
 */
class K8sConnectorCommandValidationTest {

    @Test
    void testAllowedVerbsGet() {
        // get 命令应该被接受（但实际执行会失败，因为没有真实 K8s 集群）
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        // get pods 是允许的 verb，但会因为没有真实集群而失败
        ExecResult result = connector.execute("invalid-kubeconfig", node, "get pods", options);

        // 验证不是因为 verb 验证失败，而是因为连接失败
        assertFalse(result.getStderr().contains("not allowed"));
    }

    @Test
    void testBlockedVerbApply() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "apply -f deployment.yaml", options);

        // 应该拒绝 apply 命令
        assertTrue(result.getStderr().contains("not allowed") ||
                   result.getStderr().contains("Kubectl verb 'apply'"),
                   "Should reject 'apply' verb");
        assertEquals(-1, result.getExitCode());
    }

    @Test
    void testBlockedVerbDelete() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "delete pod my-pod", options);

        assertTrue(result.getStderr().contains("not allowed") ||
                   result.getStderr().contains("Kubectl verb 'delete'"),
                   "Should reject 'delete' verb");
        assertEquals(-1, result.getExitCode());
    }

    @Test
    void testBlockedVerbCreate() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "create deployment nginx --image=nginx", options);

        assertTrue(result.getStderr().contains("not allowed") ||
                   result.getStderr().contains("Kubectl verb 'create'"),
                   "Should reject 'create' verb");
        assertEquals(-1, result.getExitCode());
    }

    @Test
    void testBlockedVerbEdit() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "edit deployment nginx", options);

        assertTrue(result.getStderr().contains("not allowed") ||
                   result.getStderr().contains("Kubectl verb 'edit'"),
                   "Should reject 'edit' verb");
        assertEquals(-1, result.getExitCode());
    }

    @Test
    void testBlockedVerbPatch() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "patch deployment nginx -p '{}'", options);

        assertTrue(result.getStderr().contains("not allowed") ||
                   result.getStderr().contains("Kubectl verb 'patch'"),
                   "Should reject 'patch' verb");
        assertEquals(-1, result.getExitCode());
    }

    @Test
    void testEmptyCommandRejected() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        ExecResult result = connector.execute("", node, "", options);

        assertTrue(result.getStderr().contains("Empty") || result.getExitCode() == -1,
                   "Should reject empty command");
    }

    @Test
    void testCaseInsensitiveVerbMatching() {
        K8sConnector connector = new K8sConnector();
        NodeEntity node = createTestNode();

        ExecOptions options = ExecOptions.builder()
                .timeoutSeconds(10)
                .maxOutputBytes(32000)
                .build();

        // GET (大写) 应该和 get 一样被处理
        ExecResult result = connector.execute("", node, "GET pods", options);

        // 不应该因为大小写问题被拒绝
        assertFalse(result.getStderr().contains("GET") && result.getStderr().contains("not allowed"));
    }

    private NodeEntity createTestNode() {
        NodeEntity node = new NodeEntity();
        node.setId("test-k8s-node");
        node.setAlias("test-k8s");
        node.setConnectorType("k8s");
        return node;
    }
}
