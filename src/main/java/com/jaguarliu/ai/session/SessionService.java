package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.storage.repository.MessageRepository;
import com.jaguarliu.ai.storage.repository.RunRepository;
import com.jaguarliu.ai.storage.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Session 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final MessageRepository messageRepository;

    /**
     * 创建新 Session（主会话）
     */
    @Transactional
    public SessionEntity create(String name) {
        return create(name, "main");
    }

    /**
     * 创建新 Session（指定 agentId）
     */
    @Transactional
    public SessionEntity create(String name, String agentId) {
        SessionEntity session = SessionEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name != null ? name : "New Session")
                .agentId(agentId)
                .sessionKind("main")
                .build();

        session = sessionRepository.save(session);
        log.info("Created session: id={}, name={}, agentId={}", session.getId(), session.getName(), agentId);
        return session;
    }

    /**
     * 创建子代理会话
     *
     * @param parentSessionId 父会话 ID
     * @param createdByRunId  创建此子会话的运行 ID
     * @param agentId         Agent Profile ID
     * @param taskSummary     任务摘要（用于生成会话名称）
     * @return 新创建的子代理会话
     */
    @Transactional
    public SessionEntity createSubagentSession(String parentSessionId,
                                                String createdByRunId,
                                                String agentId,
                                                String taskSummary) {
        String sessionId = UUID.randomUUID().toString();
        String sessionKey = String.format("agent:%s:subagent:%s", agentId, sessionId);
        String name = generateSubagentSessionName(taskSummary);

        SessionEntity session = SessionEntity.builder()
                .id(sessionId)
                .name(name)
                .agentId(agentId)
                .sessionKind("subagent")
                .sessionKey(sessionKey)
                .parentSessionId(parentSessionId)
                .createdByRunId(createdByRunId)
                .build();

        session = sessionRepository.save(session);
        log.info("Created subagent session: id={}, parentSessionId={}, createdByRunId={}, agentId={}, sessionKey={}",
                session.getId(), parentSessionId, createdByRunId, agentId, sessionKey);
        return session;
    }

    /**
     * 生成子代理会话名称
     */
    private String generateSubagentSessionName(String taskSummary) {
        if (taskSummary == null || taskSummary.isBlank()) {
            return "SubAgent Task";
        }
        // 截取前 50 个字符作为名称
        String name = taskSummary.trim();
        if (name.length() > 50) {
            name = name.substring(0, 47) + "...";
        }
        return name;
    }

    /**
     * 创建定时任务会话（session_kind = "scheduled"，不在主列表中显示）
     */
    @Transactional
    public SessionEntity createScheduledSession(String name) {
        SessionEntity session = SessionEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name != null ? name : "Scheduled Task")
                .sessionKind("scheduled")
                .build();

        session = sessionRepository.save(session);
        log.info("Created scheduled session: id={}, name={}", session.getId(), session.getName());
        return session;
    }

    /**
     * 获取所有主会话（按创建时间倒序）
     */
    public List<SessionEntity> list() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 获取所有主会话（排除子代理会话）
     */
    public List<SessionEntity> listMainSessions() {
        return sessionRepository.findBySessionKindOrderByCreatedAtDesc("main");
    }

    /**
     * 获取指定父会话的所有子代理会话
     */
    public List<SessionEntity> listSubagentSessions(String parentSessionId) {
        return sessionRepository.findByParentSessionIdOrderByCreatedAtDesc(parentSessionId);
    }

    /**
     * 重命名 Session
     */
    @Transactional
    public SessionEntity rename(String id, String newName) {
        SessionEntity session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        session.setName(newName);
        return sessionRepository.save(session);
    }

    /**
     * 根据 ID 获取 Session
     */
    public Optional<SessionEntity> get(String id) {
        return sessionRepository.findById(id);
    }

    /**
     * 根据 sessionKey 获取 Session
     */
    public Optional<SessionEntity> getBySessionKey(String sessionKey) {
        return sessionRepository.findBySessionKey(sessionKey);
    }

    /**
     * 删除 Session（级联删除关联的 messages 和 runs）
     */
    @Transactional
    public boolean delete(String id) {
        if (!sessionRepository.existsById(id)) {
            return false;
        }

        messageRepository.deleteBySessionId(id);
        runRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);

        log.info("Deleted session and related data: id={}", id);
        return true;
    }
}
