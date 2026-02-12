package com.jaguarliu.ai.datasource.connector;

import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;

/**
 * 数据源连接器接口
 *
 * 定义所有数据源实现必须遵守的契约
 * 遵循单一职责原则：只负责数据源的连接和数据操作
 */
public interface DataSourceConnector extends AutoCloseable {

    /**
     * 建立连接
     * @throws ConnectorException 连接失败时抛出
     */
    void connect() throws ConnectorException;

    /**
     * 关闭连接
     * @throws ConnectorException 关闭失败时抛出
     */
    void disconnect() throws ConnectorException;

    /**
     * 测试连接是否正常
     * @return 连接测试结果
     */
    ConnectionTestResult testConnection();

    /**
     * 执行查询
     * @param query 查询语句或文件操作描述
     * @param maxRows 最大返回行数（0表示使用默认值）
     * @param timeoutSeconds 超时时间（秒，0表示使用默认值）
     * @return 查询结果
     * @throws ConnectorException 查询失败时抛出
     */
    QueryResult executeQuery(String query, int maxRows, int timeoutSeconds) throws ConnectorException;

    /**
     * 获取 Schema 元数据
     * @return Schema 元数据
     * @throws ConnectorException 获取失败时抛出
     */
    SchemaMetadata getSchemaMetadata() throws ConnectorException;

    /**
     * 健康检查
     * @return 是否健康
     */
    boolean isHealthy();

    /**
     * 获取连接器类型
     * @return 连接器类型描述
     */
    String getConnectorType();

    /**
     * AutoCloseable 实现
     */
    @Override
    default void close() throws Exception {
        disconnect();
    }
}
