package com.miniclaw.gateway.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miniclaw.gateway.rpc.model.RpcEventFrame;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayEventBusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPublishEventsWithConnectionSessionAndRequestMetadata() {
        GatewayEventBus eventBus = new GatewayEventBus();
        RpcEventFrame frame = RpcEventFrame.builder()
                .requestId("req-001")
                .sessionId("session-001")
                .name("chat.delta")
                .payload(payload("delta", "hello"))
                .build();

        GatewayEvent published = GatewayEvent.outbound("connection-1", "session-001", "req-001", frame);
        GatewayEvent observed = eventBus.events()
                .take(1)
                .timeout(Duration.ofSeconds(1))
                .doOnSubscribe(subscription -> eventBus.publish(published))
                .blockFirst();

        assertEquals("connection-1", observed.getConnectionId());
        assertEquals("session-001", observed.getSessionId());
        assertEquals("req-001", observed.getRequestId());
        assertEquals("chat.delta", ((RpcEventFrame) observed.getFrame()).getName());
    }

    @Test
    void outboundDispatcherShouldOnlyEmitFramesForMatchingConnection() {
        GatewayEventBus eventBus = new GatewayEventBus();
        OutboundDispatcher dispatcher = new OutboundDispatcher(eventBus, objectMapper);

        String outboundJson = dispatcher.outboundJson("connection-2")
                .take(1)
                .timeout(Duration.ofSeconds(1))
                .doOnSubscribe(subscription -> {
                    eventBus.publish(GatewayEvent.outbound(
                            "connection-1",
                            "session-001",
                            "req-001",
                            RpcEventFrame.builder()
                                    .requestId("req-001")
                                    .sessionId("session-001")
                                    .name("chat.delta")
                                    .payload(payload("delta", "ignored"))
                                    .build()
                    ));
                    eventBus.publish(GatewayEvent.outbound(
                            "connection-2",
                            "session-002",
                            "req-002",
                            RpcEventFrame.builder()
                                    .requestId("req-002")
                                    .sessionId("session-002")
                                    .name("chat.delta")
                                    .payload(payload("delta", "accepted"))
                                    .build()
                    ));
                })
                .blockFirst();

        assertEquals(
                "{\"type\":\"event\",\"requestId\":\"req-002\",\"sessionId\":\"session-002\",\"name\":\"chat.delta\",\"payload\":{\"delta\":\"accepted\"}}",
                outboundJson
        );
    }

    private ObjectNode payload(String key, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(key, value);
        return payload;
    }
}
