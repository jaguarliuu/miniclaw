package com.jaguarliu.ai.datasource.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

/**
 * JDBC 数据库连接配置
 * 值对象：不可变
 *
 * 注意：密码不会存储在数据库的 JSON 字段中，而是加密存储在单独的字段中
 */
@Getter
public class JdbcConnectionConfig extends ConnectionConfig {

    private final String host;
    private final Integer port;
    private final String database;
    private final String username;
    private final String password;  // 接收前端传入，但不会序列化到数据库 JSON
    private final Map<String, String> properties;  // 额外的连接属性

    @JsonCreator
    public JdbcConnectionConfig(
            @JsonProperty("host") String host,
            @JsonProperty("port") Integer port,
            @JsonProperty("database") String database,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("properties") Map<String, String> properties) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    @Override
    public boolean isValid() {
        return host != null && !host.isBlank()
                && port != null && port > 0
                && database != null && !database.isBlank()
                && username != null && !username.isBlank();
    }

    @Override
    public String getDescription() {
        return String.format("%s@%s:%d/%s", username, host, port, database);
    }

    /**
     * 构建 JDBC URL
     */
    public String buildJdbcUrl(DataSourceType type) {
        return switch (type) {
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, database);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s",
                    host, port, database);
            case ORACLE -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    host, port, database);
            case GAUSS -> String.format("jdbc:opengauss://%s:%d/%s",
                    host, port, database);
            default -> throw new IllegalArgumentException("Unsupported JDBC type: " + type);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JdbcConnectionConfig that = (JdbcConnectionConfig) o;
        return Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(database, that.database) &&
                Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, database, username);
    }
}
