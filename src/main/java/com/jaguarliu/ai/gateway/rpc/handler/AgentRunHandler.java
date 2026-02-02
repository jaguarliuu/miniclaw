package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * agent.run 处理器
 * 创建 run 并执行 LLM 调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunHandler implements RpcHandler {

    private final SessionService sessionService;
    private final RunService runService;
    private final LlmClient llmClient;

    @Override
    public String getMethod() {
        return "agent.run";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String sessionId = extractSessionId(request.getPayload());
        String prompt = extractPrompt(request.getPayload());

        if (prompt == null || prompt.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing prompt"));
        }

        return Mono.fromCallable(() -> {
            // 如果没有 sessionId，自动创建一个
            if (sessionId == null || sessionId.isBlank()) {
                SessionEntity session = sessionService.create("New Session");
                return executeRun(session.getId(), prompt, request.getId());
            }

            // 检查 session 是否存在
            if (sessionService.get(sessionId).isEmpty()) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Session not found: " + sessionId);
            }

            return executeRun(sessionId, prompt, request.getId());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private RpcResponse executeRun(String sessionId, String prompt, String requestId) {
        // 1. 创建 run（状态为 queued）
        RunEntity run = runService.create(sessionId, prompt);

        try {
            // 2. 更新状态为 running
            runService.updateStatus(run.getId(), RunStatus.RUNNING);

            // 3. 调用 LLM（流式收集）
            StringBuilder content = new StringBuilder();
            LlmRequest llmRequest = LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user(prompt)))
                    .build();

            llmClient.stream(llmRequest)
                    .doOnNext(chunk -> {
                        if (chunk.getDelta() != null) {
                            content.append(chunk.getDelta());
                        }
                    })
                    .blockLast();

            // 4. 更新状态为 done
            runService.updateStatus(run.getId(), RunStatus.DONE);

            log.info("Run completed: id={}, content length={}", run.getId(), content.length());

            return RpcResponse.success(requestId, Map.of(
                    "runId", run.getId(),
                    "sessionId", sessionId,
                    "status", RunStatus.DONE.getValue(),
                    "content", content.toString()
            ));

        } catch (Exception e) {
            log.error("Run failed: id={}", run.getId(), e);
            // 更新状态为 error
            try {
                runService.updateStatus(run.getId(), RunStatus.ERROR);
            } catch (Exception ignored) {}

            return RpcResponse.error(requestId, "RUN_ERROR", e.getMessage());
        }
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private String extractPrompt(Object payload) {
        if (payload instanceof Map) {
            Object prompt = ((Map<?, ?>) payload).get("prompt");
            return prompt != null ? prompt.toString() : null;
        }
        return null;
    }
}
