package com.jaguarliu.ai.datasource.infrastructure.persistence.jpa;

import com.jaguarliu.ai.datasource.domain.DataSourceStatus;
import com.jaguarliu.ai.datasource.infrastructure.persistence.entity.DataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 数据源 JPA Repository
 */
@Repository
public interface JpaDataSourceRepository extends JpaRepository<DataSourceEntity, String> {

    List<DataSourceEntity> findByStatus(DataSourceStatus status);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}
