package com.jaguarliu.ai.datasource.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 连接配置抽象基类
 * 使用 Jackson 多态序列化，根据 type 字段自动选择子类
 *
 * 遵循 DDD 值对象模式：不可变、无标识
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = JdbcConnectionConfig.class, name = "jdbc"),
    @JsonSubTypes.Type(value = FileConnectionConfig.class, name = "file")
})
public abstract class ConnectionConfig {

    /**
     * 验证配置是否有效
     * 由子类实现具体的验证逻辑
     * @JsonIgnore 防止序列化时包含此派生属性
     */
    @JsonIgnore
    public abstract boolean isValid();

    /**
     * 获取配置描述（用于日志）
     * @JsonIgnore 防止序列化时包含此派生属性
     */
    @JsonIgnore
    public abstract String getDescription();
}
