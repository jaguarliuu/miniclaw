package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * agent.cancel 处理器
 * 取消正在执行的 run
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentCancelHandler implements RpcHandler {

    private final CancellationManager cancellationManager;
    private final RunService runService;

    @Override
    public String getMethod() {
        return "agent.cancel";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String runId = extractRunId(request.getPayload());

        if (runId == null || runId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing runId"));
        }

        // 检查 run 是否存在
        var runOpt = runService.get(runId);
        if (runOpt.isEmpty()) {
            return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", "Run not found: " + runId));
        }

        // 检查 run 是否可以取消
        var run = runOpt.get();
        RunStatus currentStatus = RunStatus.fromValue(run.getStatus());
        if (!currentStatus.canTransitionTo(RunStatus.CANCELED)) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_STATE",
                    "Cannot cancel run in status: " + currentStatus.getValue()));
        }

        // 请求取消
        cancellationManager.requestCancel(runId);
        log.info("Cancel requested: runId={}, connectionId={}", runId, connectionId);

        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "runId", runId,
                "cancelled", true,
                "message", "Cancellation requested"
        )));
    }

    private String extractRunId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("runId");
            return id != null ? id.toString() : null;
        }
        return null;
    }
}
