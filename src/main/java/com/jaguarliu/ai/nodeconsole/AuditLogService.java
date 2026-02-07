package com.jaguarliu.ai.nodeconsole;

import com.jaguarliu.ai.tools.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务
 * 记录命令执行和节点管理操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    private static final int MAX_SUMMARY_LENGTH = 500;

    /**
     * 记录命令执行（在 RemoteExecTool/KubectlExecTool 中调用）
     */
    public void logCommandExecution(String eventType, String nodeAlias, String nodeId,
                                     String connectorType, String toolName, String command,
                                     String safetyLevel, String safetyPolicy,
                                     boolean hitlRequired, String hitlDecision,
                                     String resultStatus, String resultSummary, long durationMs) {
        // 从 ToolExecutionContext 获取 runId/sessionId
        String runId = null;
        String sessionId = null;
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) {
            runId = ctx.getRunId();
            sessionId = ctx.getSessionId();
        }

        AuditLogEntity entity = AuditLogEntity.builder()
                .eventType(eventType)
                .runId(runId)
                .sessionId(sessionId)
                .nodeAlias(nodeAlias)
                .nodeId(nodeId)
                .connectorType(connectorType)
                .toolName(toolName)
                .command(command)
                .safetyLevel(safetyLevel)
                .safetyPolicy(safetyPolicy)
                .hitlRequired(hitlRequired)
                .hitlDecision(hitlDecision)
                .resultStatus(resultStatus)
                .resultSummary(truncate(resultSummary))
                .durationMs((int) durationMs)
                .build();

        try {
            auditLogRepository.save(entity);
            log.debug("Audit log recorded: type={}, node={}, status={}", eventType, nodeAlias, resultStatus);
        } catch (Exception e) {
            log.error("Failed to record audit log: type={}, node={}", eventType, nodeAlias, e);
        }
    }

    /**
     * 记录节点管理操作（在 NodeService 中调用）
     */
    public void logNodeOperation(String eventType, String nodeAlias, String nodeId,
                                  String connectorType, String resultStatus, String resultSummary) {
        AuditLogEntity entity = AuditLogEntity.builder()
                .eventType(eventType)
                .nodeAlias(nodeAlias)
                .nodeId(nodeId)
                .connectorType(connectorType)
                .resultStatus(resultStatus)
                .resultSummary(truncate(resultSummary))
                .hitlRequired(false)
                .build();

        try {
            auditLogRepository.save(entity);
            log.debug("Audit log recorded: type={}, node={}, status={}", eventType, nodeAlias, resultStatus);
        } catch (Exception e) {
            log.error("Failed to record audit log: type={}, node={}", eventType, nodeAlias, e);
        }
    }

    /**
     * 分页查询审计日志（支持多种筛选条件，一次只按一个条件筛选）
     */
    public Page<AuditLogEntity> query(String nodeAlias, String eventType, String safetyLevel,
                                       String resultStatus, String sessionId, int page, int size) {
        var pageable = PageRequest.of(page, size);

        if (nodeAlias != null && !nodeAlias.isBlank()) {
            return auditLogRepository.findByNodeAliasOrderByCreatedAtDesc(nodeAlias, pageable);
        }
        if (eventType != null && !eventType.isBlank()) {
            return auditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable);
        }
        if (safetyLevel != null && !safetyLevel.isBlank()) {
            return auditLogRepository.findBySafetyLevelOrderByCreatedAtDesc(safetyLevel, pageable);
        }
        if (resultStatus != null && !resultStatus.isBlank()) {
            return auditLogRepository.findByResultStatusOrderByCreatedAtDesc(resultStatus, pageable);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return auditLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable);
        }

        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String truncate(String text) {
        if (text == null) return null;
        if (text.length() <= MAX_SUMMARY_LENGTH) return text;
        return text.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }
}
