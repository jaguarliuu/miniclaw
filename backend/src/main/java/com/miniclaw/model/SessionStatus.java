package com.miniclaw.model;

/**
 * 会话状态枚举
 * 
 * 定义会话的所有可能状态
 * 
 * 为什么需要状态字段？
 * - 跟踪会话生命周期：创建 → 进行中 → 结束
 * - 可以根据状态进行过滤和统计
 * - 支持会话暂停/恢复功能
 */
public enum SessionStatus {
    ACTIVE,     // 活跃中：会话正在进行
    ARCHIVED,   // 已归档：会话被归档，不再活跃
    ENDED       // 已结束：会话已结束
}
