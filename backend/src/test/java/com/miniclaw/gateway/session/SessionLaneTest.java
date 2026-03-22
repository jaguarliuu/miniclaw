package com.miniclaw.gateway.session;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLaneTest {

    @Test
    void shouldRunTasksSequentiallyWithinSameSession() throws InterruptedException {
        SessionLane sessionLane = new SessionLane();
        Sinks.One<String> firstGate = Sinks.one();
        List<String> execution = new CopyOnWriteArrayList<>();
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        AtomicReference<String> secondResult = new AtomicReference<>();

        sessionLane.submit("session-001", () -> Mono.defer(() -> {
            execution.add("first-start");
            return firstGate.asMono()
                    .doOnSuccess(ignored -> execution.add("first-end"));
        })).subscribe(ignored -> firstDone.countDown());

        sessionLane.submit("session-001", () -> Mono.fromCallable(() -> {
            execution.add("second-start");
            return "second-result";
        })).subscribe(result -> {
            secondResult.set(result);
            secondDone.countDown();
        });

        Thread.sleep(100);
        assertEquals(List.of("first-start"), execution);

        firstGate.tryEmitValue("first-result");

        assertTrue(firstDone.await(1, TimeUnit.SECONDS));
        assertTrue(secondDone.await(1, TimeUnit.SECONDS));
        assertEquals("second-result", secondResult.get());
        assertEquals(List.of("first-start", "first-end", "second-start"), execution);
    }

    @Test
    void shouldAllowDifferentSessionsToRunInParallel() throws InterruptedException {
        SessionLane sessionLane = new SessionLane();
        Sinks.One<String> firstGate = Sinks.one();
        CountDownLatch secondStarted = new CountDownLatch(1);

        sessionLane.submit("session-001", () -> Mono.defer(() -> firstGate.asMono()))
                .subscribe();

        sessionLane.submit("session-002", () -> Mono.fromCallable(() -> {
            secondStarted.countDown();
            return "parallel";
        })).subscribe();

        assertTrue(secondStarted.await(1, TimeUnit.SECONDS));
        firstGate.tryEmitValue("done");
    }

    @Test
    void shouldContinueProcessingAfterPreviousTaskFails() {
        SessionLane sessionLane = new SessionLane();

        Mono<String> first = sessionLane.submit(
                "session-001",
                () -> Mono.error(new IllegalStateException("boom"))
        );

        Mono<String> second = sessionLane.submit(
                "session-001",
                () -> Mono.just("after-error")
        );

        String result = first.onErrorResume(ignored -> Mono.empty())
                .then(second)
                .block(Duration.ofSeconds(1));

        assertEquals("after-error", result);
    }
}
