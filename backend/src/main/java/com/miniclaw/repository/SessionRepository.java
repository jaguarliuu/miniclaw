package com.miniclaw.repository;

import com.miniclaw.model.Session;
import com.miniclaw.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话数据访问层
 * 
 * 为什么需要 Repository？
 * - 封装数据库操作，业务层不需要关心 SQL 细节
 * - Spring Data JPA 自动实现常用方法，减少样板代码
 * - 统一的数据访问接口，便于切换数据库
 * 
 * 为什么继承 JpaRepository？
 * - JpaRepository 提供了丰富的 CRUD 方法
 * - 自动实现：save、findById、findAll、delete 等
 * - 支持方法名自动推导查询（如 findByUserId）
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    
    /**
     * 根据用户 ID 查找所有会话
     * 
     * Spring Data JPA 会自动根据方法名生成 SQL：
     * SELECT * FROM sessions WHERE user_id = ? AND deleted = false
     * 
     * 为什么加 deleted = false？
     * - 实现软删除，查询时自动过滤已删除数据
     * - 用户看不到已删除的会话，但数据仍然存在
     */
    List<Session> findByUserIdAndDeletedFalse(String userId);
    
    /**
     * 根据 ID 和用户 ID 查找会话
     * 
     * 为什么需要同时匹配 ID 和用户 ID？
     * - 安全性：防止用户 A 访问用户 B 的会话
     * - 返回 Optional，调用方需要处理"不存在"的情况
     */
    Optional<Session> findByIdAndUserIdAndDeletedFalse(String id, String userId);
    
    /**
     * 根据状态查找会话
     * 
     * 用于管理功能，如：
     * - 查找所有活跃会话
     * - 归档长期不活跃的会话
     */
    List<Session> findByStatusAndDeletedFalse(SessionStatus status);
    
    /**
     * 统计用户的会话数量
     * 
     * 用于：
     * - 用户使用统计
     * - 限制免费用户的会话数量
     */
    long countByUserIdAndDeletedFalse(String userId);
}
