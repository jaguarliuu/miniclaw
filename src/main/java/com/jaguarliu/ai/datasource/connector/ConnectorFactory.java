package com.jaguarliu.ai.datasource.connector;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;

/**
 * 连接器工厂接口
 *
 * 使用工厂模式创建不同类型的数据源连接器
 * 遵循开放封闭原则：对扩展开放，对修改封闭
 */
public interface ConnectorFactory {

    /**
     * 创建数据源连接器
     *
     * @param type 数据源类型
     * @param connectionConfig 连接配置
     * @param securityConfig 安全配置
     * @return 数据源连接器实例
     * @throws ConnectorException 创建失败时抛出
     */
    DataSourceConnector createConnector(
            DataSourceType type,
            ConnectionConfig connectionConfig,
            SecurityConfig securityConfig) throws ConnectorException;

    /**
     * 判断是否支持指定的数据源类型
     *
     * @param type 数据源类型
     * @return 是否支持
     */
    boolean supports(DataSourceType type);
}
