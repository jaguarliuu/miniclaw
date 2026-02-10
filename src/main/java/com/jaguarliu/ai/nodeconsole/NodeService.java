package com.jaguarliu.ai.nodeconsole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 节点管理服务
 * 凭据仅在方法局部变量中存在，不进入返回值、日志、事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final CredentialCipher credentialCipher;
    private final ConnectorFactory connectorFactory;
    private final NodeConsoleProperties properties;

    /**
     * 注册新节点
     */
    public NodeEntity register(String alias, String displayName, String connectorType,
                                String host, Integer port, String username, String authType,
                                String rawCredential, String tags, String safetyPolicy) {
        if (nodeRepository.existsByAlias(alias)) {
            throw new IllegalArgumentException("Node alias already exists: " + alias);
        }

        CredentialCipher.EncryptedPayload encrypted = credentialCipher.encrypt(rawCredential);

        NodeEntity node = NodeEntity.builder()
                .alias(alias)
                .displayName(displayName)
                .connectorType(connectorType)
                .host(host)
                .port(port)
                .username(username)
                .authType(authType)
                .encryptedCredential(encrypted.ciphertext())
                .credentialIv(encrypted.iv())
                .tags(tags)
                .safetyPolicy(safetyPolicy != null ? safetyPolicy : properties.getDefaultSafetyPolicy())
                .build();

        NodeEntity saved = nodeRepository.save(node);
        log.info("Registered node: alias={}, type={}", alias, connectorType);
        return saved;
    }

    /**
     * 更新节点信息
     */
    public NodeEntity update(String id, String alias, String displayName, String connectorType,
                              String host, Integer port, String username, String authType,
                              String rawCredential, String tags, String safetyPolicy) {
        NodeEntity node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));

        if (alias != null && !alias.equals(node.getAlias()) && nodeRepository.existsByAlias(alias)) {
            throw new IllegalArgumentException("Node alias already exists: " + alias);
        }

        if (alias != null) node.setAlias(alias);
        if (displayName != null) node.setDisplayName(displayName);
        if (connectorType != null) node.setConnectorType(connectorType);
        if (host != null) node.setHost(host);
        if (port != null) node.setPort(port);
        if (username != null) node.setUsername(username);
        if (authType != null) node.setAuthType(authType);
        if (tags != null) node.setTags(tags);
        if (safetyPolicy != null) node.setSafetyPolicy(safetyPolicy);

        // 如果提供了新凭据，重新加密
        if (rawCredential != null && !rawCredential.isBlank()) {
            CredentialCipher.EncryptedPayload encrypted = credentialCipher.encrypt(rawCredential);
            node.setEncryptedCredential(encrypted.ciphertext());
            node.setCredentialIv(encrypted.iv());
        }

        NodeEntity saved = nodeRepository.save(node);
        log.info("Updated node: alias={}", saved.getAlias());
        return saved;
    }

    /**
     * 列出所有节点（不含凭据）
     */
    public List<NodeEntity> listAll() {
        return nodeRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 删除节点
     */
    public void remove(String id) {
        NodeEntity node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));
        nodeRepository.delete(node);
        log.info("Removed node: alias={}", node.getAlias());
    }

    /**
     * 测试节点连接
     */
    public boolean testConnection(String id) {
        NodeEntity node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + id));

        String credential = credentialCipher.decrypt(node.getEncryptedCredential(), node.getCredentialIv());
        Connector connector = connectorFactory.get(node.getConnectorType());

        boolean success;
        try {
            success = connector.testConnection(credential, node);
        } catch (Exception e) {
            log.warn("Connection test failed for node {}: {}", node.getAlias(), e.getMessage());
            success = false;
        }

        node.setLastTestedAt(LocalDateTime.now());
        node.setLastTestSuccess(success);
        nodeRepository.save(node);

        log.info("Connection test for node {}: {}", node.getAlias(), success ? "success" : "failed");
        return success;
    }

    /**
     * 执行远程命令
     */
    public String executeCommand(String alias, String command) {
        NodeEntity node = nodeRepository.findByAlias(alias)
                .orElseThrow(() -> new IllegalArgumentException("Node not found: " + alias));

        String credential = credentialCipher.decrypt(node.getEncryptedCredential(), node.getCredentialIv());
        Connector connector = connectorFactory.get(node.getConnectorType());

        if (connector == null) {
            throw new IllegalStateException("Unsupported connector type: " + node.getConnectorType());
        }

        log.info("Executing command on node {}: {}", alias, command);

        // 使用配置的超时和输出限制
        int timeout = properties.getExecTimeoutSeconds();
        int maxOutput = properties.getMaxOutputLength();

        ExecResult result = connector.execute(credential, node, command, timeout, maxOutput);

        // 格式化输出（包含截断/超时提示）
        return result.formatOutput();
    }

    /**
     * 按别名查找节点（不含凭据解密）
     */
    public Optional<NodeEntity> findByAlias(String alias) {
        return nodeRepository.findByAlias(alias);
    }

    /**
     * 获取节点安全策略
     */
    public String getSafetyPolicy(String alias) {
        return nodeRepository.findByAlias(alias)
                .map(NodeEntity::getSafetyPolicy)
                .orElse(properties.getDefaultSafetyPolicy());
    }

    /**
     * 返回简化节点列表给 LLM（无凭据）
     */
    public List<Map<String, Object>> listForLlm() {
        return nodeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(node -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("alias", node.getAlias());
                    info.put("type", node.getConnectorType());
                    if (node.getDisplayName() != null) info.put("displayName", node.getDisplayName());
                    if (node.getHost() != null) info.put("host", node.getHost());
                    if (node.getTags() != null) info.put("tags", node.getTags());
                    info.put("safetyPolicy", node.getSafetyPolicy());
                    if (node.getLastTestSuccess() != null) {
                        info.put("lastTestSuccess", node.getLastTestSuccess());
                    }
                    return info;
                })
                .toList();
    }

    /**
     * 返回简化节点列表给 LLM（按类型过滤）
     */
    public List<Map<String, Object>> listForLlm(String type, String tag) {
        List<NodeEntity> nodes = nodeRepository.findAllByOrderByCreatedAtDesc();

        return nodes.stream()
                .filter(n -> type == null || type.isBlank() || n.getConnectorType().equals(type))
                .filter(n -> tag == null || tag.isBlank() || (n.getTags() != null && n.getTags().contains(tag)))
                .map(node -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("alias", node.getAlias());
                    info.put("type", node.getConnectorType());
                    if (node.getDisplayName() != null) info.put("displayName", node.getDisplayName());
                    if (node.getHost() != null) info.put("host", node.getHost());
                    if (node.getTags() != null) info.put("tags", node.getTags());
                    info.put("safetyPolicy", node.getSafetyPolicy());
                    if (node.getLastTestSuccess() != null) {
                        info.put("lastTestSuccess", node.getLastTestSuccess());
                    }
                    return info;
                })
                .toList();
    }

    /**
     * 将 NodeEntity 转换为前端 DTO（显式排除凭据字段）
     */
    public static Map<String, Object> toNodeDto(NodeEntity node) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", node.getId());
        dto.put("alias", node.getAlias());
        dto.put("displayName", node.getDisplayName());
        dto.put("connectorType", node.getConnectorType());
        dto.put("host", node.getHost());
        dto.put("port", node.getPort());
        dto.put("username", node.getUsername());
        dto.put("authType", node.getAuthType());
        // 显式排除 encryptedCredential 和 credentialIv
        dto.put("tags", node.getTags());
        dto.put("safetyPolicy", node.getSafetyPolicy());
        dto.put("lastTestedAt", node.getLastTestedAt() != null ? node.getLastTestedAt().toString() : null);
        dto.put("lastTestSuccess", node.getLastTestSuccess());
        dto.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().toString() : null);
        dto.put("updatedAt", node.getUpdatedAt() != null ? node.getUpdatedAt().toString() : null);
        return dto;
    }
}
