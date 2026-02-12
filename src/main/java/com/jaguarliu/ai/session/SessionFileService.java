package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import com.jaguarliu.ai.storage.repository.SessionFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Session 文件追踪服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionFileService {

    private final SessionFileRepository sessionFileRepository;

    /**
     * 记录一个文件到 session
     */
    @Transactional
    public SessionFileEntity record(String sessionId, String runId,
                                     String filePath, String fileName, long fileSize) {
        SessionFileEntity entity = SessionFileEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .runId(runId)
                .filePath(filePath)
                .fileName(fileName)
                .fileSize(fileSize)
                .build();

        entity = sessionFileRepository.save(entity);
        log.info("Recorded session file: sessionId={}, runId={}, path={}, size={}",
                sessionId, runId, filePath, fileSize);
        return entity;
    }

    /**
     * 获取 session 的所有文件
     */
    public List<SessionFileEntity> listBySession(String sessionId) {
        return sessionFileRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 删除 session 的所有文件记录
     */
    @Transactional
    public void deleteBySession(String sessionId) {
        sessionFileRepository.deleteBySessionId(sessionId);
        log.info("Deleted file records for session: {}", sessionId);
    }
}
