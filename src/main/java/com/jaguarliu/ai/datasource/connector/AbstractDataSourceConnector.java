package com.jaguarliu.ai.datasource.connector;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源连接器抽象基类
 *
 * 提供连接器的通用实现逻辑
 * 子类只需实现特定于数据源类型的方法
 */
@Slf4j
@Getter
public abstract class AbstractDataSourceConnector implements DataSourceConnector {

    /** 连接配置 */
    protected final ConnectionConfig connectionConfig;

    /** 安全配置 */
    protected final SecurityConfig securityConfig;

    /** 连接状态 */
    protected volatile boolean connected = false;

    protected AbstractDataSourceConnector(
            ConnectionConfig connectionConfig,
            SecurityConfig securityConfig) {
        if (connectionConfig == null || !connectionConfig.isValid()) {
            throw new IllegalArgumentException("Invalid connection config");
        }
        if (securityConfig == null || !securityConfig.isValid()) {
            throw new IllegalArgumentException("Invalid security config");
        }
        this.connectionConfig = connectionConfig;
        this.securityConfig = securityConfig;
    }

    @Override
    public ConnectionTestResult testConnection() {
        long startTime = System.currentTimeMillis();
        try {
            // 如果未连接，先建立连接
            if (!connected) {
                connect();
            }

            // 执行健康检查
            if (!isHealthy()) {
                return ConnectionTestResult.failure("Connection is not healthy");
            }

            long responseTime = System.currentTimeMillis() - startTime;
            String details = String.format("Connected to %s", connectionConfig.getDescription());
            return ConnectionTestResult.success(responseTime, details);

        } catch (ConnectorException e) {
            log.error("Connection test failed: {}", e.getMessage(), e);
            long responseTime = System.currentTimeMillis() - startTime;
            return ConnectionTestResult.failure(responseTime, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during connection test: {}", e.getMessage(), e);
            return ConnectionTestResult.failure(e.getMessage());
        } finally {
            // 测试完成后断开连接
            try {
                if (connected) {
                    disconnect();
                }
            } catch (ConnectorException e) {
                log.warn("Failed to disconnect after test: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isHealthy() {
        return connected;
    }

    /**
     * 验证是否已连接
     * @throws ConnectorException 如果未连接
     */
    protected void ensureConnected() throws ConnectorException {
        if (!connected) {
            throw new ConnectorException(
                    "Not connected. Please call connect() first.",
                    ConnectorException.ErrorType.CONNECTION_FAILED);
        }
    }

    /**
     * 获取有效的最大行数限制
     */
    protected int getEffectiveMaxRows(int requestedMaxRows) {
        if (requestedMaxRows <= 0) {
            return securityConfig.getMaxResultRows();
        }
        // 取请求值和配置值的较小值
        return Math.min(requestedMaxRows, securityConfig.getMaxResultRows());
    }

    /**
     * 获取有效的超时时间
     */
    protected int getEffectiveTimeout(int requestedTimeout) {
        if (requestedTimeout <= 0) {
            return securityConfig.getQueryTimeout();
        }
        // 取请求值和配置值的较小值
        return Math.min(requestedTimeout, securityConfig.getQueryTimeout());
    }

    @Override
    public void close() throws Exception {
        if (connected) {
            try {
                disconnect();
            } catch (ConnectorException e) {
                log.error("Error closing connector: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
}
