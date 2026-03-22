package com.miniclaw.gateway.session;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
public class SessionLane {

    private final ConcurrentHashMap<String, Lane> lanes = new ConcurrentHashMap<>();

    public <T> Mono<T> submit(String sessionId, Supplier<Mono<T>> taskSupplier) {
        return Mono.create(sink -> lane(sessionId).enqueue(new LaneTask<>(taskSupplier, sink)));
    }

    private Lane lane(String sessionId) {
        return lanes.computeIfAbsent(sessionId, ignored -> new Lane(sessionId));
    }

    private final class Lane {

        private final String sessionId;
        private final ConcurrentLinkedQueue<LaneTask<?>> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        private Lane(String sessionId) {
            this.sessionId = sessionId;
        }

        private void enqueue(LaneTask<?> task) {
            queue.add(task);
            drain();
        }

        private void drain() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            runNext();
        }

        private void runNext() {
            LaneTask<?> task = queue.poll();
            if (task == null) {
                running.set(false);
                if (queue.isEmpty()) {
                    lanes.remove(sessionId, this);
                    return;
                }
                drain();
                return;
            }

            task.execute()
                    .doFinally(ignored -> runNext())
                    .subscribe();
        }
    }

    private static final class LaneTask<T> {

        private final Supplier<Mono<T>> taskSupplier;
        private final MonoSink<T> sink;

        private LaneTask(Supplier<Mono<T>> taskSupplier, MonoSink<T> sink) {
            this.taskSupplier = taskSupplier;
            this.sink = sink;
        }

        private Mono<Void> execute() {
            return Mono.defer(taskSupplier)
                    .doOnSuccess(sink::success)
                    .doOnError(sink::error)
                    .onErrorResume(ignored -> Mono.empty())
                    .then();
        }
    }
}
