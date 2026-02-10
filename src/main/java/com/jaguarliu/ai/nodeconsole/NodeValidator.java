package com.jaguarliu.ai.nodeconsole;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点配置校验器：确保节点配置安全合规
 */
@Component
public class NodeValidator {

    /**
     * 校验节点配置（在保存前调用）
     *
     * @param connectorType 连接器类型
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名
     * @throws IllegalArgumentException 如果校验失败
     */
    public void validate(String connectorType, String host, Integer port, String username) {
        List<String> errors = new ArrayList<>();

        if (connectorType == null || connectorType.isBlank()) {
            errors.add("connectorType is required");
        }

        // SSH 特定校验
        if ("ssh".equals(connectorType)) {
            validateSshNode(host, port, username, errors);
        }

        // K8s 特定校验
        if ("k8s".equals(connectorType)) {
            validateK8sNode(host, port, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Node validation failed: " + String.join("; ", errors));
        }
    }

    /**
     * SSH 节点校验
     */
    private void validateSshNode(String host, Integer port, String username, List<String> errors) {
        // 必填字段
        if (host == null || host.isBlank()) {
            errors.add("host is required for SSH nodes");
        } else {
            // 禁止 localhost/127.0.0.1（安全风险：可能在网关本机执行命令）
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                errors.add("host cannot be localhost or 127.0.0.1 (security risk: commands would run on gateway itself)");
            }
        }

        if (port == null) {
            errors.add("port is required for SSH nodes");
        } else if (port < 1 || port > 65535) {
            errors.add("port must be between 1 and 65535");
        }

        if (username == null || username.isBlank()) {
            errors.add("username is required for SSH nodes");
        } else {
            // 禁止 root 用户（安全最佳实践）
            if ("root".equals(username)) {
                errors.add("username cannot be 'root' (security best practice: use sudo instead)");
            }
        }
    }

    /**
     * K8s 节点校验
     */
    private void validateK8sNode(String host, Integer port, List<String> errors) {
        // K8s 节点通常通过 kubeconfig 连接，host/port 可选
        // 如果提供了 host，也要校验安全性
        if (host != null && !host.isBlank()) {
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                errors.add("host cannot be localhost for K8s nodes");
            }
        }

        if (port != null && (port < 1 || port > 65535)) {
            errors.add("port must be between 1 and 65535");
        }
    }
}
