package dev.nagarjuna.orders.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for the outbox relay.
 *
 * The two numbers that matter in production are how many events are waiting and how old the
 * oldest one is. A growing backlog with a growing age means Kafka is unreachable or the relay
 * has stopped. The gauges read cached values that are refreshed on a schedule, so a Prometheus
 * scrape never runs a query against the database.
 */
@Component
public class OutboxMetrics {

    private final OutboxRepository outbox;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final Counter published;
    private final Counter publishFailures;

    public OutboxMetrics(OutboxRepository outbox, MeterRegistry registry, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
        Gauge.builder("outbox.pending", pending, AtomicLong::get)
                .description("Events written to the outbox but not yet sent to Kafka")
                .register(registry);
        Gauge.builder("outbox.oldest.age", oldestAgeSeconds, AtomicLong::get)
                .description("Age of the oldest unsent outbox event")
                .baseUnit("seconds")
                .register(registry);
        this.published = Counter.builder("outbox.published")
                .description("Events sent to Kafka by the relay")
                .register(registry);
        this.publishFailures = Counter.builder("outbox.publish.failures")
                .description("Failed attempts to send an outbox event to Kafka")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${outbox.metrics.refresh-interval:5s}")
    public void refresh() {
        pending.set(outbox.countByPublishedAtIsNull());
        oldestAgeSeconds.set(outbox.findOldestUnpublishedCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, clock.instant()).toSeconds()))
                .orElse(0L));
    }

    void recordPublished() {
        published.increment();
    }

    void recordFailure() {
        publishFailures.increment();
    }
}
