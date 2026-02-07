package com.jaguarliu.ai.gateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcEvent;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * 事件总线
 * 负责 Agent 事件的发布和订阅
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventBus {

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * 全局事件流
     */
    private final Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 多线程安全的 EmitFailureHandler
     * 当并发线程同时 emit 导致 FAIL_NON_SERIALIZED 时自旋重试
     */
    private static final Sinks.EmitFailureHandler RETRY_NON_SERIALIZED =
            Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1));

    /**
     * 发布事件
     */
    public void publish(AgentEvent event) {
        log.debug("Publishing event: type={}, runId={}, connectionId={}",
                event.getType(), event.getRunId(), event.getConnectionId());

        // 直接推送到对应的 WebSocket 连接
        pushToConnection(event);

        // 同时发布到全局流（供其他订阅者使用）
        // 使用 busyLooping 策略处理多线程并发 emit（subagent 并行场景）
        sink.emitNext(event, RETRY_NON_SERIALIZED);
    }

    /**
     * 订阅指定 runId 的事件
     */
    public Flux<AgentEvent> subscribe(String runId) {
        return sink.asFlux()
                .filter(event -> runId.equals(event.getRunId()));
    }

    /**
     * 订阅所有事件
     */
    public Flux<AgentEvent> subscribeAll() {
        return sink.asFlux();
    }

    /**
     * 推送事件到 WebSocket 连接
     */
    private void pushToConnection(AgentEvent event) {
        String connectionId = event.getConnectionId();
        if (connectionId == null) {
            log.warn("Event has no connectionId, cannot push: runId={}", event.getRunId());
            return;
        }

        // 定时任务使用占位 connectionId，无需推送到 WebSocket
        if ("__scheduled__".equals(connectionId)) {
            return;
        }

        WebSocketSession session = connectionManager.get(connectionId);
        if (session == null) {
            log.warn("Connection not found: connectionId={}", connectionId);
            return;
        }

        try {
            RpcEvent rpcEvent = RpcEvent.of(
                    event.getType().getValue(),
                    event.getRunId(),
                    event.getData()
            );
            String json = objectMapper.writeValueAsString(rpcEvent);

            session.send(Flux.just(session.textMessage(json)))
                    .subscribe(
                            null,
                            e -> log.error("Failed to send event: connectionId={}, runId={}",
                                    connectionId, event.getRunId(), e)
                    );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event", e);
        }
    }
}
