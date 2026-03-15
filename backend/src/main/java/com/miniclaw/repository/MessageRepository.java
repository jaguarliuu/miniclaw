package com.miniclaw.repository;

import com.miniclaw.model.Message;
import com.miniclaw.model.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问层
 * 
 * 负责消息的增删改查操作
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    
    /**
     * 根据会话 ID 查找所有消息
     * 
     * 为什么用 sessionId 而不是 Session 对象？
     * - 只需要 session_id 外键值，不需要加载整个 Session
     * - 减少数据库查询次数
     * 
     * 为什么按创建时间排序？
     * - 消息是按时间顺序显示的
     * - 保证对话的连贯性
     */
    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    
    /**
     * 根据会话 ID 和角色查找消息
     * 
     * 用途：
     * - 只获取用户消息用于分析
     * - 只获取助手消息用于训练
     */
    List<Message> findBySessionIdAndRoleOrderByCreatedAtAsc(String sessionId, MessageRole role);
    
    /**
     * 统计会话的消息数量
     * 
     * 用于：
     * - 显示会话消息数
     * - 限制会话长度（防止 token 超限）
     */
    long countBySessionId(String sessionId);
    
    /**
     * 删除会话的所有消息
     * 
     * 当删除会话时，需要级联删除所有消息
     * 
     * 为什么不是物理删除？
     * - 消息也可能需要软删除（根据业务需求）
     * - 当前实现是物理删除，后续可以改为软删除
     */
    void deleteBySessionId(String sessionId);
    
    /**
     * 获取会话最近的 N 条消息
     * 
     * 为什么需要这个方法？
     * - LLM API 有 token 限制，不能发送全部历史
     * - 只发送最近的几条消息作为上下文
     * 
     * 为什么用 Session 对象而不是 sessionId？
     * - 需要排序后限制数量
     * - 可以结合其他条件查询
     */
    List<Message> findTop10BySessionIdOrderByCreatedAtDesc(String sessionId);
}
