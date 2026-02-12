package com.jaguarliu.ai.datasource.application.dto;

import com.jaguarliu.ai.datasource.domain.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 数据源 DTO
 */
@Getter
@Builder
public class DataSourceDTO {

    private String id;
    private String name;
    private DataSourceType type;
    private ConnectionConfig connectionConfig;
    private SecurityConfig securityConfig;
    private DataSourceStatus status;
    private LocalDateTime lastTestedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从领域对象转换为 DTO
     * 注意：为了安全，不返回密码信息
     */
    public static DataSourceDTO fromDomain(DataSource dataSource) {
        ConnectionConfig connectionConfig = dataSource.getConnectionConfig();

        // 如果是 JDBC 配置，创建不带密码的副本用于返回
        if (connectionConfig instanceof JdbcConnectionConfig jdbcConfig) {
            connectionConfig = new JdbcConnectionConfig(
                    jdbcConfig.getHost(),
                    jdbcConfig.getPort(),
                    jdbcConfig.getDatabase(),
                    jdbcConfig.getUsername(),
                    null,  // 密码不返回给前端
                    jdbcConfig.getProperties()
            );
        }

        return DataSourceDTO.builder()
                .id(dataSource.getId())
                .name(dataSource.getName())
                .type(dataSource.getType())
                .connectionConfig(connectionConfig)
                .securityConfig(dataSource.getSecurityConfig())
                .status(dataSource.getStatus())
                .lastTestedAt(dataSource.getLastTestedAt())
                .lastError(dataSource.getLastError())
                .createdAt(dataSource.getCreatedAt())
                .updatedAt(dataSource.getUpdatedAt())
                .build();
    }
}
