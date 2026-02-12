package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    List<SessionEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 按 sessionKind 查询（排除子代理会话或只查子代理）
     */
    List<SessionEntity> findBySessionKindOrderByCreatedAtDesc(String sessionKind);

    /**
     * 查询指定父会话的所有子代理会话
     */
    List<SessionEntity> findByParentSessionIdOrderByCreatedAtDesc(String parentSessionId);

    /**
     * 根据 sessionKey 查询（唯一索引）
     */
    Optional<SessionEntity> findBySessionKey(String sessionKey);

    /**
     * 根据创建来源 run 查询
     */
    List<SessionEntity> findByCreatedByRunId(String createdByRunId);

    /**
     * 按创建时间范围查询（用于每日回顾）
     */
    List<SessionEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
