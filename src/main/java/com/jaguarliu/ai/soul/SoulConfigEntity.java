package com.jaguarliu.ai.soul;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 灵魂配置
 * 定义 Agent 的人设、性格、回答风格等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "soul_config")
public class SoulConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Agent 名称
     */
    @Column(name = "agent_name", length = 100)
    private String agentName;

    /**
     * 人设描述
     */
    @Column(name = "personality", length = 2000)
    private String personality;

    /**
     * 性格特点（JSON 数组）
     * 例如：["专业", "友好", "幽默"]
     */
    @Column(name = "traits", columnDefinition = "TEXT")
    private String traits;

    /**
     * 回答风格
     * formal/casual
     */
    @Column(name = "response_style", length = 50)
    private String responseStyle;

    /**
     * 详细程度
     * concise/balanced/detailed
     */
    @Column(name = "detail_level", length = 50)
    private String detailLevel;

    /**
     * 专业领域（JSON 数组）
     * 例如：["编程", "AI", "系统架构"]
     */
    @Column(name = "expertise", columnDefinition = "TEXT")
    private String expertise;

    /**
     * 禁忌话题（JSON 数组）
     */
    @Column(name = "forbidden_topics", columnDefinition = "TEXT")
    private String forbiddenTopics;

    /**
     * 自定义系统提示词补充
     */
    @Column(name = "custom_prompt", columnDefinition = "TEXT")
    private String customPrompt;

    /**
     * 是否启用
     */
    @Column(name = "enabled")
    private boolean enabled;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
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
