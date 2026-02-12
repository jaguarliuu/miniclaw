package com.jaguarliu.ai.datasource.infrastructure.persistence.converter;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSource;
import com.jaguarliu.ai.datasource.domain.JdbcConnectionConfig;
import com.jaguarliu.ai.datasource.infrastructure.persistence.entity.DataSourceEntity;
import com.jaguarliu.ai.nodeconsole.CredentialCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据源领域对象与实体转换器
 * 负责密码的加解密处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceConverter {

    private final CredentialCipher credentialCipher;

    /**
     * 领域对象转实体（加密密码）
     */
    public DataSourceEntity toEntity(DataSource domain) {
        ConnectionConfig connectionConfig = domain.getConnectionConfig();
        String encryptedPassword = null;
        String passwordIv = null;
        ConnectionConfig configForStorage = connectionConfig;  // 默认直接存储

        // 如果是 JDBC 连接且有密码，进行加密并创建不带密码的配置用于存储
        if (connectionConfig instanceof JdbcConnectionConfig jdbcConfig) {
            String password = jdbcConfig.getPassword();
            if (password != null && !password.isBlank()) {
                // 加密密码
                CredentialCipher.EncryptedPayload encrypted = credentialCipher.encrypt(password);
                encryptedPassword = encrypted.ciphertext();
                passwordIv = encrypted.iv();

                // 创建不带密码的配置用于存储到 JSON
                configForStorage = new JdbcConnectionConfig(
                        jdbcConfig.getHost(),
                        jdbcConfig.getPort(),
                        jdbcConfig.getDatabase(),
                        jdbcConfig.getUsername(),
                        null,  // 密码设为 null
                        jdbcConfig.getProperties()
                );

                log.debug("Password encrypted for data source: {}", domain.getName());
            }
        }

        return DataSourceEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .type(domain.getType())
                .connectionConfig(configForStorage)  // 存储不带密码的配置
                .securityConfig(domain.getSecurityConfig())
                .status(domain.getStatus())
                .lastTestedAt(domain.getLastTestedAt())
                .lastError(domain.getLastError())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .encryptedPassword(encryptedPassword)
                .passwordIv(passwordIv)
                .build();
    }

    /**
     * 实体转领域对象（解密密码）
     */
    public DataSource toDomain(DataSourceEntity entity) {
        ConnectionConfig connectionConfig = entity.getConnectionConfig();

        // 如果是 JDBC 连接且有加密密码，进行解密并重新创建带密码的 ConnectionConfig
        if (connectionConfig instanceof JdbcConnectionConfig jdbcConfig
                && entity.getEncryptedPassword() != null
                && entity.getPasswordIv() != null) {

            String decryptedPassword = credentialCipher.decrypt(
                    entity.getEncryptedPassword(),
                    entity.getPasswordIv()
            );

            // 重新创建带密码的 ConnectionConfig
            connectionConfig = new JdbcConnectionConfig(
                    jdbcConfig.getHost(),
                    jdbcConfig.getPort(),
                    jdbcConfig.getDatabase(),
                    jdbcConfig.getUsername(),
                    decryptedPassword,
                    jdbcConfig.getProperties()
            );

            log.debug("Password decrypted for data source: {}", entity.getName());
        }

        return DataSource.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .connectionConfig(connectionConfig)
                .securityConfig(entity.getSecurityConfig())
                .status(entity.getStatus())
                .lastTestedAt(entity.getLastTestedAt())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

