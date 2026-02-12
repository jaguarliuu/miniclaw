package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * session.files.list 处理器
 * 获取指定 session 的文件列表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionFilesListHandler implements RpcHandler {

    private final SessionFileService sessionFileService;

    @Override
    public String getMethod() {
        return "session.files.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String sessionId = extractSessionId(request.getPayload());

        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing sessionId"));
        }

        return Mono.fromCallable(() -> {
            List<SessionFileEntity> files = sessionFileService.listBySession(sessionId);
            List<Map<String, Object>> fileDtos = files.stream()
                    .map(this::toFileDto)
                    .toList();
            return RpcResponse.success(request.getId(), Map.of("files", fileDtos));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private Map<String, Object> toFileDto(SessionFileEntity file) {
        return Map.of(
                "id", file.getId(),
                "sessionId", file.getSessionId(),
                "runId", file.getRunId() != null ? file.getRunId() : "",
                "filePath", file.getFilePath(),
                "fileName", file.getFileName(),
                "fileSize", file.getFileSize(),
                "createdAt", file.getCreatedAt().toString()
        );
    }
}
