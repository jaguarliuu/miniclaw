package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.SubagentOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubagentOutboxRepository extends JpaRepository<SubagentOutboxEntity, String> {

    List<SubagentOutboxEntity> findByStatusOrderByCreatedAtAsc(String status);

    List<SubagentOutboxEntity> findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            String status,
            LocalDateTime nextRetryAt
    );
}

