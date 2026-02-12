package com.jaguarliu.ai.datasource.domain.repository;

import com.jaguarliu.ai.datasource.domain.DataSource;

import java.util.List;
import java.util.Optional;

/**
 * 数据源仓储接口
 *
 * 定义数据源聚合根的持久化契约
 * 遵循 DDD 仓储模式
 */
public interface DataSourceRepository {

    /**
     * 保存数据源（新增或更新）
     */
    DataSource save(DataSource dataSource);

    /**
     * 根据 ID 查找数据源
     */
    Optional<DataSource> findById(String id);

    /**
     * 查找所有数据源
     */
    List<DataSource> findAll();

    /**
     * 根据状态查找数据源
     */
    List<DataSource> findByStatus(com.jaguarliu.ai.datasource.domain.DataSourceStatus status);

    /**
     * 根据 ID 删除数据源
     */
    void deleteById(String id);

    /**
     * 检查数据源名称是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查数据源名称是否存在（排除指定 ID）
     */
    boolean existsByNameAndIdNot(String name, String excludeId);
}
