package com.jaguarliu.ai.datasource.application.service.impl;

import com.jaguarliu.ai.datasource.application.dto.*;
import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import com.jaguarliu.ai.datasource.connector.ConnectionTestResult;
import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.connector.ConnectorFactory;
import com.jaguarliu.ai.datasource.connector.DataSourceConnector;
import com.jaguarliu.ai.datasource.domain.*;
import com.jaguarliu.ai.datasource.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据源应用服务实现
 *
 * 编排领域对象完成业务用例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataSourceRepository repository;
    private final ConnectorFactory connectorFactory;

    @Override
    @Transactional
    public DataSourceDTO createDataSource(CreateDataSourceRequest request) {
        log.info("Creating data source: {}", request.getName());

        // 验证名称唯一性
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("数据源名称已存在: " + request.getName());
        }

        // 验证配置
        if (!request.getConnectionConfig().isValid()) {
            throw new IllegalArgumentException("连接配置无效");
        }

        // 使用默认安全配置（如果未提供）
        SecurityConfig securityConfig = request.getSecurityConfig() != null
                ? request.getSecurityConfig()
                : SecurityConfig.createDefault();

        if (!securityConfig.isValid()) {
            throw new IllegalArgumentException("安全配置无效");
        }

        // 创建数据源聚合根
        DataSource dataSource = DataSource.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .type(request.getType())
                .connectionConfig(request.getConnectionConfig())
                .securityConfig(securityConfig)
                .status(DataSourceStatus.INACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 持久化
        DataSource saved = repository.save(dataSource);

        log.info("Data source created successfully: id={}, name={}", saved.getId(), saved.getName());
        return DataSourceDTO.fromDomain(saved);
    }

    @Override
    @Transactional
    public DataSourceDTO updateDataSource(String id, UpdateDataSourceRequest request) {
        log.info("Updating data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        // 更新名称
        if (request.getName() != null && !request.getName().equals(dataSource.getName())) {
            if (repository.existsByNameAndIdNot(request.getName(), id)) {
                throw new IllegalArgumentException("数据源名称已存在: " + request.getName());
            }
            dataSource.setName(request.getName());
        }

        // 更新连接配置
        if (request.getConnectionConfig() != null) {
            if (!request.getConnectionConfig().isValid()) {
                throw new IllegalArgumentException("连接配置无效");
            }
            ConnectionConfig newConfig = request.getConnectionConfig();
            // 如果未提供密码，保留现有密码
            if (newConfig instanceof JdbcConnectionConfig newJdbc
                    && dataSource.getConnectionConfig() instanceof JdbcConnectionConfig existingJdbc) {
                if (newJdbc.getPassword() == null || newJdbc.getPassword().isBlank()) {
                    newConfig = new JdbcConnectionConfig(
                            newJdbc.getHost(),
                            newJdbc.getPort(),
                            newJdbc.getDatabase(),
                            newJdbc.getUsername(),
                            existingJdbc.getPassword(),
                            newJdbc.getProperties()
                    );
                }
            }
            dataSource.setConnectionConfig(newConfig);
            // 配置变更后，需要重新测试
            dataSource.setStatus(DataSourceStatus.INACTIVE);
        }

        // 更新安全配置
        if (request.getSecurityConfig() != null) {
            if (!request.getSecurityConfig().isValid()) {
                throw new IllegalArgumentException("安全配置无效");
            }
            dataSource.setSecurityConfig(request.getSecurityConfig());
        }

        dataSource.setUpdatedAt(LocalDateTime.now());

        DataSource saved = repository.save(dataSource);
        log.info("Data source updated successfully: {}", id);

        return DataSourceDTO.fromDomain(saved);
    }

    @Override
    @Transactional
    public void deleteDataSource(String id) {
        log.info("Deleting data source: {}", id);

        if (!repository.findById(id).isPresent()) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }

        repository.deleteById(id);
        log.info("Data source deleted successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public DataSourceDTO getDataSource(String id) {
        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));
        return DataSourceDTO.fromDomain(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataSourceDTO> listDataSources() {
        return repository.findAll().stream()
                .map(DataSourceDTO::fromDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConnectionTestResult testConnection(String id) {
        log.info("Testing connection for data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        return testConnectionInternal(dataSource);
    }

    @Override
    public ConnectionTestResult testConnection(CreateDataSourceRequest request) {
        log.info("Testing connection for new data source: {}", request.getName());

        // 创建临时数据源对象用于测试
        SecurityConfig securityConfig = request.getSecurityConfig() != null
                ? request.getSecurityConfig()
                : SecurityConfig.createDefault();

        DataSource tempDataSource = DataSource.builder()
                .id("temp-" + UUID.randomUUID())
                .name(request.getName())
                .type(request.getType())
                .connectionConfig(request.getConnectionConfig())
                .securityConfig(securityConfig)
                .status(DataSourceStatus.INACTIVE)
                .build();

        return testConnectionInternal(tempDataSource);
    }

    @Override
    @Transactional
    public void enableDataSource(String id) {
        log.info("Enabling data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        dataSource.enable();
        repository.save(dataSource);

        log.info("Data source enabled: {}", id);
    }

    @Override
    @Transactional
    public void disableDataSource(String id) {
        log.info("Disabling data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        dataSource.disable();
        repository.save(dataSource);

        log.info("Data source disabled: {}", id);
    }

    @Override
    public QueryResult executeQuery(String id, String query, Integer maxRows, Integer timeoutSeconds) {
        log.info("Executing query on data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        if (!dataSource.isUsable()) {
            throw new IllegalStateException("数据源不可用: " + id);
        }

        try (DataSourceConnector connector = connectorFactory.createConnector(
                dataSource.getType(),
                dataSource.getConnectionConfig(),
                dataSource.getSecurityConfig())) {

            connector.connect();

            int effectiveMaxRows = maxRows != null ? maxRows : 0;
            int effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : 0;

            return connector.executeQuery(query, effectiveMaxRows, effectiveTimeout);

        } catch (ConnectorException e) {
            log.error("Query execution failed: {}", e.getMessage(), e);
            return QueryResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during query: {}", e.getMessage(), e);
            return QueryResult.failure("查询失败: " + e.getMessage());
        }
    }

    @Override
    public SchemaMetadata getSchemaMetadata(String id) {
        log.info("Getting schema metadata for data source: {}", id);

        DataSource dataSource = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + id));

        if (!dataSource.isUsable()) {
            throw new IllegalStateException("数据源不可用: " + id);
        }

        try (DataSourceConnector connector = connectorFactory.createConnector(
                dataSource.getType(),
                dataSource.getConnectionConfig(),
                dataSource.getSecurityConfig())) {

            connector.connect();
            return connector.getSchemaMetadata();

        } catch (ConnectorException e) {
            log.error("Failed to get schema metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting schema metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 内部方法：测试连接并更新数据源状态
     */
    private ConnectionTestResult testConnectionInternal(DataSource dataSource) {
        try (DataSourceConnector connector = connectorFactory.createConnector(
                dataSource.getType(),
                dataSource.getConnectionConfig(),
                dataSource.getSecurityConfig())) {

            ConnectionTestResult result = connector.testConnection();

            // 如果不是临时数据源，更新状态
            if (!dataSource.getId().startsWith("temp-")) {
                if (result.isSuccess()) {
                    dataSource.markConnected();
                } else {
                    dataSource.markConnectionFailed(result.getErrorMessage());
                }
                repository.save(dataSource);
            }

            return result;

        } catch (ConnectorException e) {
            log.error("Connection test failed: {}", e.getMessage(), e);
            String errorMessage = "连接测试失败: " + e.getMessage();

            if (!dataSource.getId().startsWith("temp-")) {
                dataSource.markConnectionFailed(errorMessage);
                repository.save(dataSource);
            }

            return ConnectionTestResult.failure(errorMessage);
        } catch (Exception e) {
            log.error("Unexpected error during connection test: {}", e.getMessage(), e);
            String errorMessage = "连接测试失败: " + e.getMessage();

            if (!dataSource.getId().startsWith("temp-")) {
                dataSource.markConnectionFailed(errorMessage);
                repository.save(dataSource);
            }

            return ConnectionTestResult.failure(errorMessage);
        }
    }
}
