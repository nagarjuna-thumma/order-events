package dev.nagarjuna.fulfilment.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    final RetryPolicy noJitterLow = new RetryPolicy(6, Duration.ofSeconds(10), Duration.ofMinutes(1), bound -> 0);
    final RetryPolicy noJitterHigh = new RetryPolicy(6, Duration.ofSeconds(10), Duration.ofMinutes(1), bound -> bound - 1);

    long delaySeconds(RetryPolicy policy, int attempt) {
        return Duration.between(NOW, policy.nextAttempt(attempt, NOW)).toSeconds();
    }

    @Test
    void delayDoublesThenStopsAtTheCap() {
        // Upper end of the jitter range is the full exponential delay.
        assertThat(delaySeconds(noJitterHigh, 1)).isEqualTo(10);
        assertThat(delaySeconds(noJitterHigh, 2)).isEqualTo(20);
        assertThat(delaySeconds(noJitterHigh, 3)).isEqualTo(40);
        assertThat(delaySeconds(noJitterHigh, 4)).isEqualTo(60);
        assertThat(delaySeconds(noJitterHigh, 5)).isEqualTo(60);
    }

    @Test
    void jitterNeverGoesBelowHalfTheDelay() {
        assertThat(delaySeconds(noJitterLow, 1)).isEqualTo(5);
        assertThat(delaySeconds(noJitterLow, 3)).isEqualTo(20);
        assertThat(delaySeconds(noJitterLow, 5)).isEqualTo(30);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        assertThat(noJitterHigh.nextAttempt(5, NOW)).isNotNull();
        assertThat(noJitterHigh.nextAttempt(6, NOW)).isNull();
    }

    @Test
    void onlyServerErrorsTimeoutsAndRateLimitsAreRetried() {
        assertThat(RetryPolicy.retryable(500)).isTrue();
        assertThat(RetryPolicy.retryable(503)).isTrue();
        assertThat(RetryPolicy.retryable(408)).isTrue();
        assertThat(RetryPolicy.retryable(429)).isTrue();
        assertThat(RetryPolicy.retryable(400)).isFalse();
        assertThat(RetryPolicy.retryable(401)).isFalse();
        assertThat(RetryPolicy.retryable(404)).isFalse();
    }
}
