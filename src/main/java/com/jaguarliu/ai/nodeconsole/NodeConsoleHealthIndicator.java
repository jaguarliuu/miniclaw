package com.jaguarliu.ai.nodeconsole;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Node Console 健康检查指示器
 * 验证加密服务和连接器可用性
 */
@Component
@RequiredArgsConstructor
public class NodeConsoleHealthIndicator implements HealthIndicator {

    private final CredentialCipher credentialCipher;
    private final ConnectorFactory connectorFactory;

    @Override
    public Health health() {
        try {
            // 测试加密/解密功能
            var encrypted = credentialCipher.encrypt("health-check-test");
            String decrypted = credentialCipher.decrypt(encrypted.ciphertext(), encrypted.iv());

            if (!"health-check-test".equals(decrypted)) {
                return Health.down()
                        .withDetail("error", "Encryption round-trip test failed")
                        .build();
            }

            // 检查连接器注册
            boolean hasSsh = connectorFactory.supports("ssh");
            boolean hasK8s = connectorFactory.supports("k8s");

            return Health.up()
                    .withDetail("encryption", "AES-256-GCM")
                    .withDetail("connectors", String.format("ssh=%s, k8s=%s", hasSsh, hasK8s))
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
