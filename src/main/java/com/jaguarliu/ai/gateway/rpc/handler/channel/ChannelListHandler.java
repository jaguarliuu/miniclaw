package com.jaguarliu.ai.gateway.rpc.handler.channel;

import com.jaguarliu.ai.channel.ChannelService;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelListHandler implements RpcHandler {

    private final ChannelService channelService;

    @Override
    public String getMethod() {
        return "channel.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            var channels = channelService.listAll().stream()
                    .map(ChannelService::toDto)
                    .toList();
            return RpcResponse.success(request.getId(), channels);
        });
    }
}
