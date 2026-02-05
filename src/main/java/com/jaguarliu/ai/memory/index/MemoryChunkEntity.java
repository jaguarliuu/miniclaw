package com.jaguarliu.ai.memory.index;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * memory_chunks 表实体
 *
 * 全局记忆索引，不区分会话（个人助手，非多租户）
 *
 * 注意：embedding 和 tsv 字段由原生 SQL 操作，
 * JPA 只管理基础字段。向量操作通过 Native Query 完成。
 */
@Entity
@Table(name = "memory_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryChunkEntity {

    @Id
    private String id;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_start", nullable = false)
    private int lineStart;

    @Column(name = "line_end", nullable = false)
    private int lineEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // embedding 和 tsv 由 Native Query 操作，不映射到 JPA
    // tsv 由数据库触发器自动填充

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
