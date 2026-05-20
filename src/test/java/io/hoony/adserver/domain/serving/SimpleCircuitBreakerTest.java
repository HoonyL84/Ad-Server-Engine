package io.hoony.adserver.domain.serving;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCircuitBreakerTest {

    @Test
    @DisplayName("5회 연속 실패 시 CLOSED에서 OPEN으로 전이하고 10초 대기 윈도우 동안 요청을 차단한다")
    void testStateTransitions() throws InterruptedException {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker(5, 100); // 100ms window for fast testing

        // Initial State
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();

        // 4 failures - should remain CLOSED
        for (int i = 0; i < 4; i++) {
            cb.recordFailure();
            assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);
        }

        // 5th failure - transitions to OPEN
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // During backoff window - should remain blocked
        Thread.sleep(50);
        assertThat(cb.allowRequest()).isFalse();

        // After backoff window - transitioned to HALF_OPEN
        Thread.sleep(60);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // If the HALF_OPEN probe never records a result, the breaker moves back to OPEN.
        Thread.sleep(110);
        assertThat(cb.allowRequest()).isFalse();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.OPEN);

        Thread.sleep(110);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // Failure in HALF_OPEN - transitions back to OPEN
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // Wait again for HALF_OPEN
        Thread.sleep(110);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isFalse();

        // Success in HALF_OPEN - transitions back to CLOSED
        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }
}
