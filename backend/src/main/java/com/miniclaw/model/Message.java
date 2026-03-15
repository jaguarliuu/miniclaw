package com.miniclaw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 消息实体
 * 
 * 为什么需要 Message 实体？
 * - 会话由多条消息组成，需要单独存储
 * - 每条消息有独立的角色（用户/助手/系统）
 * - 后续需要基于消息内容构建 Memory 系统
 * 
 * 消息和会话的关系：
 * - Session 1 ←→ N Message（一对多）
 * - 消息不能独立存在，必须属于某个会话
 */
@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    /**
     * 消息唯一标识
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    /**
     * 所属会话
     * 
     * @ManyToOne: 多对一关系（多条消息属于一个会话）
     * @JoinColumn: 指定外键列名
     * 
     * 为什么用 eager 还是 lazy？
     * - LAZY（懒加载）：只有在真正访问 session 属性时才查询数据库
     * - 这是 JPA 的推荐做法，避免 N+1 查询问题
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;
    
    /**
     * 消息角色
     * 
     * LLM API 使用角色来区分消息来源：
     * - user: 用户发送的消息
     * - assistant: AI 助手的回复
     * - system: 系统提示词
     * - tool: 工具调用的结果
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MessageRole role;
    
    /**
     * 消息内容
     * 
     * TEXT 类型：可以存储长文本（最多 65535 字节）
     * 如果需要存储更长的内容（如代码），可以改用 LONGTEXT
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
    
    /**
     * Token 数量（用于成本计算）
     * 
     * 为什么需要记录 token 数？
     * - LLM API 按 token 计费，需要追踪成本
     * - 可以设置 token 上限，控制费用
     * - 分析用户使用模式
     * 
     * 允许为 null，因为用户消息的 token 数可能不需要记录
     */
    @Column(name = "token_count")
    private Integer tokenCount;
    
    /**
     * 模型名称
     * 
     * 记录这条消息是用哪个模型生成的
     * 同一个会话可能在不同时间使用不同模型
     */
    @Column(name = "model")
    private String model;
    
    /**
     * 创建时间
     * 
     * 消息创建后不应该被修改，所以只有创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * 消息元数据（JSON 格式）
     * 
     * 存储额外的信息，如：
     * - 工具调用的参数和结果
     * - 响应的延迟时间
     * - 错误信息
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}

// MessageRole 枚举已移到独立文件 MessageRole.java
