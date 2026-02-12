package com.jaguarliu.ai.datasource.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 数据源聚合根
 *
 * 遵循 DDD 聚合根设计：
 * - 是聚合的入口，外部只能通过 DataSource 访问内部实体
 * - 维护聚合内部的一致性
 * - 拥有唯一标识（id）
 */
@Getter
@Builder
public class DataSource {

    /** 唯一标识 */
    private String id;

    /** 数据源名称 */
    @Setter
    private String name;

    /** 数据源类型 */
    private DataSourceType type;

    /** 连接配置（值对象） */
    @Setter
    private ConnectionConfig connectionConfig;

    /** 安全配置（值对象） */
    @Setter
    private SecurityConfig securityConfig;

    /** 数据源状态 */
    @Setter
    private DataSourceStatus status;

    /** 最后测试时间 */
    @Setter
    private LocalDateTime lastTestedAt;

    /** 最后错误信息 */
    @Setter
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Setter
    private LocalDateTime updatedAt;

    /**
     * 标记连接成功
     */
    public void markConnected() {
        this.status = DataSourceStatus.ACTIVE;
        this.lastTestedAt = LocalDateTime.now();
        this.lastError = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记连接失败
     */
    public void markConnectionFailed(String errorMessage) {
        this.status = DataSourceStatus.ERROR;
        this.lastTestedAt = LocalDateTime.now();
        this.lastError = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用数据源
     */
    public void disable() {
        this.status = DataSourceStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 启用数据源
     */
    public void enable() {
        if (this.status == DataSourceStatus.DISABLED) {
            this.status = DataSourceStatus.INACTIVE;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 验证数据源配置是否完整
     */
    public boolean isConfigValid() {
        return name != null && !name.isBlank()
                && type != null
                && connectionConfig != null && connectionConfig.isValid()
                && securityConfig != null && securityConfig.isValid();
    }

    /**
     * 是否可以使用
     */
    public boolean isUsable() {
        return status == DataSourceStatus.ACTIVE && isConfigValid();
    }
}
