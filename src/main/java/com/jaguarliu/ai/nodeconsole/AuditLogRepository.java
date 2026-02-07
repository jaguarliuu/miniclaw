package com.jaguarliu.ai.nodeconsole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {

    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLogEntity> findByNodeAliasOrderByCreatedAtDesc(String nodeAlias, Pageable pageable);

    Page<AuditLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    Page<AuditLogEntity> findBySafetyLevelOrderByCreatedAtDesc(String safetyLevel, Pageable pageable);

    Page<AuditLogEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    Page<AuditLogEntity> findByResultStatusOrderByCreatedAtDesc(String resultStatus, Pageable pageable);
}
