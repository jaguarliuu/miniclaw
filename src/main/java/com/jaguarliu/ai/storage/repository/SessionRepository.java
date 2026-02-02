package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    List<SessionEntity> findAllByOrderByCreatedAtDesc();
}
