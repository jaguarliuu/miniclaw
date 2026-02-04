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
     * 创建新 Session
     */
    @Transactional
    public SessionEntity create(String name) {
        SessionEntity session = SessionEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name != null ? name : "New Session")
                .build();

        session = sessionRepository.save(session);
        log.info("Created session: id={}, name={}", session.getId(), session.getName());
        return session;
    }

    /**
     * 获取所有 Session（按创建时间倒序）
     */
    public List<SessionEntity> list() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 根据 ID 获取 Session
     */
    public Optional<SessionEntity> get(String id) {
        return sessionRepository.findById(id);
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
