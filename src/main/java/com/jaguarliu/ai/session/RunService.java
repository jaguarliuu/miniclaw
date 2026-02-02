package com.jaguarliu.ai.session;

import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.repository.RunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Run 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;

    /**
     * 创建新 Run
     */
    @Transactional
    public RunEntity create(String sessionId, String prompt) {
        RunEntity run = RunEntity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .status(RunStatus.QUEUED.getValue())
                .prompt(prompt)
                .build();

        run = runRepository.save(run);
        log.info("Created run: id={}, sessionId={}, status={}", run.getId(), sessionId, run.getStatus());
        return run;
    }

    /**
     * 更新 Run 状态
     */
    @Transactional
    public RunEntity updateStatus(String runId, RunStatus newStatus) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        RunStatus currentStatus = RunStatus.fromValue(run.getStatus());

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s", currentStatus, newStatus));
        }

        run.setStatus(newStatus.getValue());
        run = runRepository.save(run);
        log.info("Updated run status: id={}, {} -> {}", runId, currentStatus, newStatus);
        return run;
    }

    /**
     * 获取 Run
     */
    public Optional<RunEntity> get(String runId) {
        return runRepository.findById(runId);
    }

    /**
     * 获取 Session 下的所有 Run
     */
    public List<RunEntity> listBySession(String sessionId) {
        return runRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }
}
