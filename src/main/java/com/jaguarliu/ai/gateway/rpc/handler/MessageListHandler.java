package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * message.list 处理器
 * 获取指定 session 的消息历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListHandler implements RpcHandler {

    private final MessageService messageService;

    @Override
    public String getMethod() {
        return "message.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        String sessionId = extractSessionId(request.getPayload());

        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing sessionId"));
        }

        return Mono.fromCallable(() -> {
            List<MessageEntity> messages = messageService.getSessionHistory(sessionId);
            List<Map<String, Object>> messageDtos = messages.stream()
                    .map(this::toMessageDto)
                    .toList();
            return RpcResponse.success(request.getId(), Map.of("messages", messageDtos));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractSessionId(Object payload) {
        if (payload instanceof Map) {
            Object id = ((Map<?, ?>) payload).get("sessionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private Map<String, Object> toMessageDto(MessageEntity message) {
        return Map.of(
                "id", message.getId(),
                "sessionId", message.getSessionId(),
                "runId", message.getRunId(),
                "role", message.getRole(),
                "content", message.getContent(),
                "createdAt", message.getCreatedAt().toString()
        );
    }
}
