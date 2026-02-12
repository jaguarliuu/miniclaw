package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

/**
 * run.get 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunGetHandler implements RpcHandler {

    private final RunService runService;

    @Override
    public String getMethod() {
        return "run.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String runId = extractRunId(request.getPayload());
            if (runId == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing runId");
            }

            Optional<RunEntity> run = runService.get(runId);
            if (run.isEmpty()) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Run not found: " + runId);
            }

            return RpcResponse.success(request.getId(), toRunDto(run.get()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractRunId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("runId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private Map<String, Object> toRunDto(RunEntity run) {
        return Map.of(
                "id", run.getId(),
                "sessionId", run.getSessionId(),
                "status", run.getStatus(),
                "prompt", run.getPrompt(),
                "createdAt", run.getCreatedAt().toString(),
                "updatedAt", run.getUpdatedAt().toString()
        );
    }
}
