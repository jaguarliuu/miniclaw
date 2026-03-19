package com.miniclaw.gateway.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class OutboundDispatcher {

    private final GatewayEventBus eventBus;
    private final ObjectMapper objectMapper;

    public OutboundDispatcher(GatewayEventBus eventBus, ObjectMapper objectMapper) {
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
    }

    public Flux<String> outboundJson(String connectionId) {
        return eventBus.events()
                .filter(event -> connectionId.equals(event.getConnectionId()))
                .map(event -> toJson(event.getFrame()));
    }

    private String toJson(Object frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbound frame", exception);
        }
    }
}
