package com.jaguarliu.ai.datasource.application.service;

import com.jaguarliu.ai.datasource.application.dto.*;
import com.jaguarliu.ai.datasource.connector.ConnectionTestResult;
import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;

import java.util.List;

/**
 * 数据源应用服务接口
 *
 * 定义数据源相关的应用层操作
 * 遵循应用服务模式，编排领域对象完成业务用例
 */
public interface DataSourceService {

    /**
     * 创建数据源
     */
    DataSourceDTO createDataSource(CreateDataSourceRequest request);

    /**
     * 更新数据源
     */
    DataSourceDTO updateDataSource(String id, UpdateDataSourceRequest request);

    /**
     * 删除数据源
     */
    void deleteDataSource(String id);

    /**
     * 获取数据源详情
     */
    DataSourceDTO getDataSource(String id);

    /**
     * 获取所有数据源列表
     */
    List<DataSourceDTO> listDataSources();

    /**
     * 测试数据源连接
     */
    ConnectionTestResult testConnection(String id);

    /**
     * 测试数据源连接（不保存）
     */
    ConnectionTestResult testConnection(CreateDataSourceRequest request);

    /**
     * 启用数据源
     */
    void enableDataSource(String id);

    /**
     * 禁用数据源
     */
    void disableDataSource(String id);

    /**
     * 执行查询
     */
    QueryResult executeQuery(String id, String query, Integer maxRows, Integer timeoutSeconds);

    /**
     * 获取 Schema 元数据
     */
    SchemaMetadata getSchemaMetadata(String id);
}
