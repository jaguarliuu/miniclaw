package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionFileRepository extends JpaRepository<SessionFileEntity, String> {

    List<SessionFileEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    void deleteBySessionId(String sessionId);
}
