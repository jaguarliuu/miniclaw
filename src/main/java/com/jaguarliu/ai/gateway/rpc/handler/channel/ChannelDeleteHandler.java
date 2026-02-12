package com.jaguarliu.ai.gateway.rpc.handler.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.channel.ChannelService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelDeleteHandler implements RpcHandler {

    private final ChannelService channelService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "channel.delete";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String id = (String) params.get("id");
            if (id == null || id.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "id is required");
            }

            channelService.delete(id);
            return RpcResponse.success(request.getId(), Map.of("success", true));
        }).onErrorResume(e -> {
            log.error("Failed to delete channel: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "DELETE_FAILED", e.getMessage()));
        });
    }
}
