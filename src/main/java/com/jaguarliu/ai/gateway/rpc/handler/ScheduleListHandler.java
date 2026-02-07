package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleListHandler implements RpcHandler {

    private final ScheduledTaskService scheduledTaskService;

    @Override
    public String getMethod() {
        return "schedule.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            var tasks = scheduledTaskService.listAll().stream()
                    .map(ScheduledTaskService::toDto)
                    .toList();
            return RpcResponse.success(request.getId(), tasks);
        });
    }
}
