package com.jaguarliu.ai.datasource.infrastructure.persistence.entity;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceStatus;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 数据源 JPA 实体
 */
@Entity
@Table(name = "datasources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceEntity {

    @Id
    @Column(nullable = false, length = 50)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSourceType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "TEXT")
    private ConnectionConfig connectionConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "TEXT")
    private SecurityConfig securityConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSourceStatus status;

    @Column
    private LocalDateTime lastTestedAt;

    @Column(length = 500)
    private String lastError;

    /** 加密后的密码 (AES-256-GCM) */
    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;

    /** 密码加密的初始化向量 */
    @Column(name = "password_iv", length = 44)
    private String passwordIv;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
