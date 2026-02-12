package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleCreateHandler implements RpcHandler {

    private final ScheduledTaskService scheduledTaskService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.create";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String name = (String) params.get("name");
            String cronExpr = (String) params.get("cronExpr");
            String prompt = (String) params.get("prompt");
            String channelId = (String) params.get("channelId");
            String channelType = (String) params.get("channelType");
            String emailTo = (String) params.get("emailTo");
            String emailCc = (String) params.get("emailCc");

            if (name == null || name.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "name is required");
            }
            if (cronExpr == null || cronExpr.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "cronExpr is required");
            }
            if (prompt == null || prompt.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "prompt is required");
            }
            if (channelId == null || channelId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "channelId is required");
            }
            if (channelType == null || channelType.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "channelType is required");
            }

            var task = scheduledTaskService.create(name, cronExpr, prompt, channelId, channelType, emailTo, emailCc);
            return RpcResponse.success(request.getId(), ScheduledTaskService.toDto(task));
        }).onErrorResume(e -> {
            log.error("Failed to create scheduled task: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED", e.getMessage()));
        });
    }
}
