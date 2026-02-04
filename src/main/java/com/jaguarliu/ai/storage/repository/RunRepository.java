package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunRepository extends JpaRepository<RunEntity, String> {

    List<RunEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    List<RunEntity> findByStatus(String status);

    void deleteBySessionId(String sessionId);
}
