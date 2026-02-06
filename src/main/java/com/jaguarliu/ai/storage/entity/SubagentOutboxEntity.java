package com.jaguarliu.ai.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 子任务回传 outbox 实体
 *
 * 用于在回传（announce）失败时做持久化重试。
 */
@Entity
@Table(name = "subagent_outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubagentOutboxEntity {

    @Id
    private String id;

    @Column(name = "parent_run_id", nullable = false)
    private String parentRunId;

    @Column(name = "parent_session_id", nullable = false)
    private String parentSessionId;

    @Column(name = "sub_run_id", nullable = false)
    private String subRunId;

    @Column(name = "sub_session_id", nullable = false)
    private String subSessionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (eventType == null || eventType.isBlank()) {
            eventType = "subagent.announced";
        }
        if (status == null || status.isBlank()) {
            status = "pending";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

