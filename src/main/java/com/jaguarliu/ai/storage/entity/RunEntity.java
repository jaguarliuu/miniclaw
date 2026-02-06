package com.jaguarliu.ai.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "run_kind", nullable = false)
    private String runKind;

    @Column(nullable = false)
    private String lane;

    @Column(name = "parent_run_id")
    private String parentRunId;

    @Column(name = "requester_session_id")
    private String requesterSessionId;

    @Column(nullable = false)
    private Boolean deliver;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (runKind == null || runKind.isBlank()) {
            runKind = "main";
        }
        if (lane == null || lane.isBlank()) {
            lane = "main";
        }
        if (deliver == null) {
            deliver = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
