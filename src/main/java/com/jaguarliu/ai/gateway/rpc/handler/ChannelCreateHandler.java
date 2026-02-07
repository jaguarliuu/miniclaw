package com.jaguarliu.ai.gateway.rpc.handler;

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
public class ChannelCreateHandler implements RpcHandler {

    private final ChannelService channelService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "channel.create";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);

            String name = (String) params.get("name");
            String type = (String) params.get("type");
            Object configObj = params.get("config");
            String credential = (String) params.get("credential");

            if (name == null || name.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "name is required");
            }
            if (type == null || type.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "type is required");
            }
            if (configObj == null) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "config is required");
            }

            String configJson = objectMapper.writeValueAsString(configObj);
            var channel = channelService.create(name, type, configJson, credential);
            return RpcResponse.success(request.getId(), ChannelService.toDto(channel));
        }).onErrorResume(e -> {
            log.error("Failed to create channel: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "CREATE_FAILED", e.getMessage()));
        });
    }
}
