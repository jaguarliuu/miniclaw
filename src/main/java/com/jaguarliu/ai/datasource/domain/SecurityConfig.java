package com.jaguarliu.ai.datasource.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 数据源安全配置
 * 值对象：不可变
 */
@Getter
public class SecurityConfig {

    /** 最大连接数 */
    private final int maxConnections;

    /** 最小空闲连接数 */
    private final int minIdle;

    /** 连接超时（毫秒） */
    private final long connectionTimeout;

    /** 空闲超时（毫秒） */
    private final long idleTimeout;

    /** 连接最大生命周期（毫秒） */
    private final long maxLifetime;

    /** 查询超时（秒） */
    private final int queryTimeout;

    /** 查询结果最大行数（0表示不限制） */
    private final int maxResultRows;

    /** 查询结果最大大小（字节，0表示不限制） */
    private final long maxResultSize;

    /** 是否只读（强制） */
    private final boolean readOnly;

    @JsonCreator
    public SecurityConfig(
            @JsonProperty("maxConnections") Integer maxConnections,
            @JsonProperty("minIdle") Integer minIdle,
            @JsonProperty("connectionTimeout") Long connectionTimeout,
            @JsonProperty("idleTimeout") Long idleTimeout,
            @JsonProperty("maxLifetime") Long maxLifetime,
            @JsonProperty("queryTimeout") Integer queryTimeout,
            @JsonProperty("maxResultRows") Integer maxResultRows,
            @JsonProperty("maxResultSize") Long maxResultSize,
            @JsonProperty("readOnly") Boolean readOnly) {
        this.maxConnections = maxConnections != null ? maxConnections : 10;
        this.minIdle = minIdle != null ? minIdle : 2;
        this.connectionTimeout = connectionTimeout != null ? connectionTimeout : 30000L;
        this.idleTimeout = idleTimeout != null ? idleTimeout : 600000L;
        this.maxLifetime = maxLifetime != null ? maxLifetime : 1800000L;
        this.queryTimeout = queryTimeout != null ? queryTimeout : 30;
        this.maxResultRows = maxResultRows != null ? maxResultRows : 1000;
        this.maxResultSize = maxResultSize != null ? maxResultSize : 10485760L; // 10MB
        this.readOnly = readOnly != null ? readOnly : true;
    }

    /**
     * 创建默认安全配置
     */
    public static SecurityConfig createDefault() {
        return new SecurityConfig(null, null, null, null, null, null, null, null, null);
    }

    /**
     * 验证配置是否合法
     * @JsonIgnore 防止序列化时包含此派生属性
     */
    @JsonIgnore
    public boolean isValid() {
        return maxConnections > 0
                && minIdle >= 0
                && minIdle <= maxConnections
                && connectionTimeout > 0
                && queryTimeout > 0;
    }
}
