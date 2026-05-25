package io.hoony.adserver.domain.serving;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class SimpleCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long backoffWindowMs;
    private final long backoffJitterMs;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong stateChangedAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong nextHalfOpenAt = new AtomicLong(0L);

    public SimpleCircuitBreaker(
            @Value("${ad-server.serving.cb.failure-threshold:5}") int failureThreshold,
            @Value("${ad-server.serving.cb.backoff-window-ms:10000}") long backoffWindowMs,
            @Value("${ad-server.serving.cb.backoff-jitter-ms:2000}") long backoffJitterMs
    ) {
        this.failureThreshold = failureThreshold;
        this.backoffWindowMs = backoffWindowMs;
        this.backoffJitterMs = Math.max(backoffJitterMs, 0L);
    }

    public boolean allowRequest() {
        State currentState = state.get();
        if (currentState == State.CLOSED) {
            return true;
        }

        if (currentState == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now >= nextHalfOpenAt.get()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    stateChangedAt.set(now);
                    log.info("Circuit Breaker transitioned from OPEN to HALF_OPEN. Allowing test request.");
                    return true;
                }
            }
            return false;
        }

        if (currentState == State.HALF_OPEN) {
            long now = System.currentTimeMillis();
            if (now - stateChangedAt.get() >= backoffWindowMs) {
                if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                    scheduleOpenWindow(now);
                    log.warn("Circuit Breaker HALF_OPEN probe expired. Transitioned back to OPEN.");
                }
            }
        }

        return false;
    }

    public void recordSuccess() {
        State currentState = state.get();
        if (currentState != State.CLOSED) {
            if (state.compareAndSet(currentState, State.CLOSED)) {
                failureCount.set(0);
                stateChangedAt.set(System.currentTimeMillis());
                log.info("Circuit Breaker recovered. Transitioned from {} to CLOSED.", currentState);
            }
        } else {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        int currentFailures = failureCount.incrementAndGet();
        State currentState = state.get();

        if (currentState == State.CLOSED && currentFailures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                long waitMs = scheduleOpenWindow(System.currentTimeMillis());
                log.warn("Circuit Breaker OPENED! Failure threshold reached. Blocked for {} ms.", waitMs);
            }
        } else if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                scheduleOpenWindow(System.currentTimeMillis());
                log.warn("Circuit Breaker transition from HALF_OPEN back to OPEN due to test request failure.");
            }
        }
    }

    public State getState() {
        return state.get();
    }

    private long scheduleOpenWindow(long now) {
        long jitterMs = backoffJitterMs == 0L ? 0L : ThreadLocalRandom.current().nextLong(backoffJitterMs + 1L);
        long waitMs = backoffWindowMs + jitterMs;
        stateChangedAt.set(now);
        nextHalfOpenAt.set(now + waitMs);
        return waitMs;
    }
}
