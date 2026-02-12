package com.jaguarliu.ai.datasource.connector.file;

import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.connector.ConnectorFactory;
import com.jaguarliu.ai.datasource.connector.DataSourceConnector;
import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.FileConnectionConfig;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件连接器工厂
 *
 * 负责创建文件数据源（CSV、XLSX）的连接器
 */
@Slf4j
@Component
public class FileConnectorFactory implements ConnectorFactory {

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

        if (!(connectionConfig instanceof FileConnectionConfig)) {
            throw new ConnectorException(
                    "Invalid connection config type. Expected FileConnectionConfig",
                    ConnectorException.ErrorType.CONFIGURATION_ERROR);
        }

        FileConnectionConfig fileConfig = (FileConnectionConfig) connectionConfig;

        return switch (type) {
            case CSV -> new CsvFileConnector(fileConfig, securityConfig);
            case XLSX -> new XlsxFileConnector(fileConfig, securityConfig);
            default -> throw new ConnectorException(
                    "Unsupported file type: " + type,
                    ConnectorException.ErrorType.CONFIGURATION_ERROR);
        };
    }

    @Override
    public boolean supports(DataSourceType type) {
        return "file".equals(type.getCategory());
    }
}
