package com.jaguarliu.ai.nodeconsole;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "node_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    private String id;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "node_alias", length = 100)
    private String nodeAlias;

    @Column(name = "node_id", length = 36)
    private String nodeId;

    @Column(name = "connector_type", length = 20)
    private String connectorType;

    @Column(name = "tool_name", length = 50)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String command;

    @Column(name = "safety_level", length = 20)
    private String safetyLevel;

    @Column(name = "safety_policy", length = 20)
    private String safetyPolicy;

    @Column(name = "hitl_required", nullable = false)
    private Boolean hitlRequired;

    @Column(name = "hitl_decision", length = 20)
    private String hitlDecision;

    @Column(name = "result_status", nullable = false, length = 20)
    private String resultStatus;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (hitlRequired == null) {
            hitlRequired = false;
        }
        createdAt = LocalDateTime.now();
    }
}
