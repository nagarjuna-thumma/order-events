package dev.nagarjuna.fulfilment.webhook;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongUnaryOperator;

/**
 * Exponential backoff with jitter. The delay doubles each attempt up to a cap, then a random
 * amount between half and all of it is used, so a receiver that comes back after an outage isn't
 * hit by every queued delivery in the same second.
 */
final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initial;
    private final Duration max;
    private final LongUnaryOperator random; // returns a value in [0, bound)

    RetryPolicy(int maxAttempts, Duration initial, Duration max, LongUnaryOperator random) {
        this.maxAttempts = maxAttempts;
        this.initial = initial;
        this.max = max;
        this.random = random;
    }

    /** When to try again after the given attempt (1-based) failed, or null to give up. */
    Instant nextAttempt(int attemptsSoFar, Instant now) {
        if (attemptsSoFar >= maxAttempts) return null;
        long base = initial.toMillis() << Math.min(attemptsSoFar - 1, 30);
        long capped = Math.min(base, max.toMillis());
        long half = capped / 2;
        return now.plusMillis(half + random.applyAsLong(capped - half + 1));
    }

    /** 4xx means the request itself is wrong and sending it again won't help, except for these two. */
    static boolean retryable(int statusCode) {
        return statusCode >= 500 || statusCode == 408 || statusCode == 429;
    }
}
