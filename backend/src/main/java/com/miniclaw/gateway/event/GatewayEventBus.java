package com.miniclaw.gateway.event;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class GatewayEventBus {

    private final Sinks.Many<GatewayEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(GatewayEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            throw new IllegalStateException("Failed to publish gateway event: " + result);
        }
    }

    public Flux<GatewayEvent> events() {
        return sink.asFlux();
    }
}
