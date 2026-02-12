package com.jaguarliu.ai.datasource.infrastructure.persistence.repository;

import com.jaguarliu.ai.datasource.domain.DataSource;
import com.jaguarliu.ai.datasource.domain.DataSourceStatus;
import com.jaguarliu.ai.datasource.domain.repository.DataSourceRepository;
import com.jaguarliu.ai.datasource.infrastructure.persistence.converter.DataSourceConverter;
import com.jaguarliu.ai.datasource.infrastructure.persistence.entity.DataSourceEntity;
import com.jaguarliu.ai.datasource.infrastructure.persistence.jpa.JpaDataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据源仓储实现
 *
 * 适配 JPA Repository 到领域仓储接口
 */
@Repository
@RequiredArgsConstructor
public class DataSourceRepositoryImpl implements DataSourceRepository {

    private final JpaDataSourceRepository jpaRepository;
    private final DataSourceConverter converter;

    @Override
    public DataSource save(DataSource dataSource) {
        DataSourceEntity entity = converter.toEntity(dataSource);
        DataSourceEntity saved = jpaRepository.save(entity);
        return converter.toDomain(saved);
    }

    @Override
    public Optional<DataSource> findById(String id) {
        return jpaRepository.findById(id)
                .map(converter::toDomain);
    }

    @Override
    public List<DataSource> findAll() {
        return jpaRepository.findAll().stream()
                .map(converter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<DataSource> findByStatus(DataSourceStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(converter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String excludeId) {
        return jpaRepository.existsByNameAndIdNot(name, excludeId);
    }
}
