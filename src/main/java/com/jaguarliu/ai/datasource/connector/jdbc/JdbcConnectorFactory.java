package com.jaguarliu.ai.datasource.connector.jdbc;

import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.connector.ConnectorFactory;
import com.jaguarliu.ai.datasource.connector.DataSourceConnector;
import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.JdbcConnectionConfig;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JDBC 连接器工厂
 *
 * 负责创建各种 JDBC 数据源的连接器
 */
@Slf4j
@Component
public class JdbcConnectorFactory implements ConnectorFactory {

    @Override
    public DataSourceConnector createConnector(
            DataSourceType type,
            ConnectionConfig connectionConfig,
            SecurityConfig securityConfig) throws ConnectorException {

        if (!supports(type)) {
            throw new ConnectorException(
                    "Unsupported data source type: " + type,
                    ConnectorException.ErrorType.CONFIGURATION_ERROR);
        }

        if (!(connectionConfig instanceof JdbcConnectionConfig)) {
            throw new ConnectorException(
                    "Invalid connection config type. Expected JdbcConnectionConfig",
                    ConnectorException.ErrorType.CONFIGURATION_ERROR);
        }

        JdbcConnectionConfig jdbcConfig = (JdbcConnectionConfig) connectionConfig;
        return new JdbcDataSourceConnector(type, jdbcConfig, securityConfig);
    }

    @Override
    public boolean supports(DataSourceType type) {
        return "jdbc".equals(type.getCategory());
    }
}
