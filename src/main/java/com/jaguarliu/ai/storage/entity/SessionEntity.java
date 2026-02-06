package com.jaguarliu.ai.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "session_kind", nullable = false)
    private String sessionKind;

    @Column(name = "session_key")
    private String sessionKey;

    @Column(name = "parent_session_id")
    private String parentSessionId;

    @Column(name = "created_by_run_id")
    private String createdByRunId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (sessionKind == null || sessionKind.isBlank()) {
            sessionKind = "main";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
