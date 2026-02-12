package com.jaguarliu.ai.datasource.connector;

import com.jaguarliu.ai.datasource.connector.jdbc.JdbcConnectorFactory;
import com.jaguarliu.ai.datasource.connector.file.FileConnectorFactory;
import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认连接器工厂实现
 *
 * 聚合所有具体的连接器工厂，根据数据源类型分发创建请求
 */
@Slf4j
@Primary
@Component
public class DefaultConnectorFactory implements ConnectorFactory {

    private final List<ConnectorFactory> factories;

    public DefaultConnectorFactory(
            JdbcConnectorFactory jdbcConnectorFactory,
            FileConnectorFactory fileConnectorFactory) {
        this.factories = List.of(jdbcConnectorFactory, fileConnectorFactory);
    }

    @Override
    public DataSourceConnector createConnector(
            DataSourceType type,
            ConnectionConfig connectionConfig,
            SecurityConfig securityConfig) throws ConnectorException {

        for (ConnectorFactory factory : factories) {
            if (factory.supports(type)) {
                log.info("Creating connector for type: {} using factory: {}",
                        type, factory.getClass().getSimpleName());
                return factory.createConnector(type, connectionConfig, securityConfig);
            }
        }

        throw new ConnectorException(
                "No connector factory found for type: " + type,
                ConnectorException.ErrorType.CONFIGURATION_ERROR);
    }

    @Override
    public boolean supports(DataSourceType type) {
        return factories.stream().anyMatch(factory -> factory.supports(type));
    }
}
