package com.jaguarliu.ai.schedule;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTaskEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "cron_expr", nullable = false, length = 100)
    private String cronExpr;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "channel_id", nullable = false, length = 36)
    private String channelId;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(name = "email_to", length = 500)
    private String emailTo;

    @Column(name = "email_cc", length = 500)
    private String emailCc;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_success")
    private Boolean lastRunSuccess;

    @Column(name = "last_run_error", columnDefinition = "TEXT")
    private String lastRunError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
