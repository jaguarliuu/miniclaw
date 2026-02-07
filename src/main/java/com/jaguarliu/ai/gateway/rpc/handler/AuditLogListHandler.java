package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.AuditLogEntity;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * audit.logs.list - 分页查询审计日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogListHandler implements RpcHandler {

    private final AuditLogService auditLogService;

    @Override
    public String getMethod() {
        return "audit.logs.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String nodeAlias = extractString(request.getPayload(), "nodeAlias");
            String eventType = extractString(request.getPayload(), "eventType");
            String safetyLevel = extractString(request.getPayload(), "safetyLevel");
            String resultStatus = extractString(request.getPayload(), "resultStatus");
            String sessionId = extractString(request.getPayload(), "sessionId");
            int page = extractInt(request.getPayload(), "page", 0);
            int size = extractInt(request.getPayload(), "size", 50);

            // 限制 size 范围
            if (size < 1) size = 1;
            if (size > 100) size = 100;

            Page<AuditLogEntity> result = auditLogService.query(
                    nodeAlias, eventType, safetyLevel, resultStatus, sessionId, page, size);

            var logs = result.getContent().stream()
                    .map(AuditLogListHandler::toDto)
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("logs", logs);
            response.put("page", result.getNumber());
            response.put("size", result.getSize());
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());

            return RpcResponse.success(request.getId(), response);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static Map<String, Object> toDto(AuditLogEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("eventType", entity.getEventType());
        dto.put("runId", entity.getRunId());
        dto.put("sessionId", entity.getSessionId());
        dto.put("nodeAlias", entity.getNodeAlias());
        dto.put("connectorType", entity.getConnectorType());
        dto.put("toolName", entity.getToolName());
        dto.put("command", entity.getCommand());
        dto.put("safetyLevel", entity.getSafetyLevel());
        dto.put("safetyPolicy", entity.getSafetyPolicy());
        dto.put("hitlRequired", entity.getHitlRequired());
        dto.put("hitlDecision", entity.getHitlDecision());
        dto.put("resultStatus", entity.getResultStatus());
        dto.put("resultSummary", entity.getResultSummary());
        dto.put("durationMs", entity.getDurationMs());
        dto.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return dto;
    }

    private String extractString(Object payload, String key) {
        if (payload instanceof Map) {
            Object val = ((Map<?, ?>) payload).get(key);
            return val != null ? val.toString() : null;
        }
        return null;
    }

    private int extractInt(Object payload, String key, int defaultValue) {
        if (payload instanceof Map) {
            Object val = ((Map<?, ?>) payload).get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            if (val instanceof String) {
                try {
                    return Integer.parseInt((String) val);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }
}
