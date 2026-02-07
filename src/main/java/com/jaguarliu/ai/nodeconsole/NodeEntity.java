package com.jaguarliu.ai.nodeconsole;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String alias;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "connector_type", nullable = false, length = 20)
    private String connectorType;

    @Column
    private String host;

    @Column
    private Integer port;

    @Column(length = 100)
    private String username;

    @Column(name = "auth_type", length = 20)
    private String authType;

    @Column(name = "encrypted_credential", nullable = false, columnDefinition = "TEXT")
    private String encryptedCredential;

    @Column(name = "credential_iv", nullable = false, length = 44)
    private String credentialIv;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "safety_policy", nullable = false, length = 20)
    private String safetyPolicy;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Column(name = "last_test_success")
    private Boolean lastTestSuccess;

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
