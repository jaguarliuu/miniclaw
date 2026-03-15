package com.miniclaw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 会话实体
 * 
 * 为什么需要 Session 实体？
 * - AI Agent 的对话是有状态的，需要持久化保存
 * - 一个 Session 代表一次完整的对话会话
 * - 后续的 Memory 系统需要基于 Session 进行检索
 * 
 * 为什么用 JPA 注解？
 * - JPA 是 Java 持久化标准，不绑定特定数据库
 * - 注解方式比 XML 配置更直观，代码即文档
 */
@Entity
@Table(name = "sessions")
@Data                    // Lombok: 自动生成 getter/setter/toString/equals/hashCode
@NoArgsConstructor       // Lombok: 生成无参构造器（JPA 要求）
@AllArgsConstructor      // Lombok: 生成全参构造器
public class Session {
    
    /**
     * 会话唯一标识
     * 
     * 为什么用 UUID 而不是自增 ID？
     * - UUID 在分布式系统中不会冲突
     * - 可以在客户端生成，不需要等待数据库返回
     * - 更安全，不会被遍历猜测
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    /**
     * 会话标题（可选）
     * 
     * 可以从第一条消息自动生成，也可以用户手动设置
     */
    @Column(name = "title")
    private String title;
    
    /**
     * 用户标识
     * 
     * 用于区分不同用户的会话
     * 当前简化版使用字符串，后续可关联 User 表
     */
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    /**
     * Agent 标识
     * 
     * 一个系统可能有多个不同用途的 Agent
     * 这个字段标识当前会话使用的是哪个 Agent
     */
    @Column(name = "agent_id")
    private String agentId;
    
    /**
     * 会话状态
     * 
     * 为什么需要状态字段？
     * - 跟踪会话生命周期：创建 → 进行中 → 结束
     * - 可以根据状态进行过滤和统计
     * - 支持会话暂停/恢复功能
     */
    @Enumerated(EnumType.STRING)  // 存储枚举名称而非序号，更安全
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;
    
    /**
     * 创建时间
     * 
     * @CreationTimestamp: Hibernate 自动填充当前时间
     * 不需要手动设置，插入时自动生成
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     * 
     * @UpdateTimestamp: 每次更新时自动更新时间
     * 用于追踪会话最后活跃时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 软删除标记
     * 
     * 为什么用软删除而不是物理删除？
     * - 用户可能误删，需要恢复功能
     * - 数据分析需要历史数据
     * - 关联数据（消息）不会成为孤儿
     */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
    
    /**
     * 扩展元数据（JSON 格式）
     * 
     * 存储额外的会话信息，如：
     * - 用户选择的模型
     * - 客户端信息
     * - 自定义配置
     * 
     * 为什么用 JSON 而不是多个字段？
     * - 灵活扩展，不需要频繁改表结构
     * - 不同场景可以存储不同的元数据
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}

// SessionStatus 枚举已移到独立文件 SessionStatus.java
