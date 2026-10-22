package dev.nagarjuna.orders.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Instant now = Instant.parse("2026-10-22T12:00:00Z");
    private final OutboxMetrics metrics = new OutboxMetrics(outbox, registry, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void reportsBacklogSizeAndAgeOfOldestEvent() {
        when(outbox.countByPublishedAtIsNull()).thenReturn(42L);
        when(outbox.findOldestUnpublishedCreatedAt()).thenReturn(Optional.of(now.minusSeconds(90)));

        metrics.refresh();

        assertThat(registry.get("outbox.pending").gauge().value()).isEqualTo(42);
        assertThat(registry.get("outbox.oldest.age").gauge().value()).isEqualTo(90);
    }

    @Test
    void ageIsZeroWhenNothingIsWaiting() {
        when(outbox.countByPublishedAtIsNull()).thenReturn(0L);
        when(outbox.findOldestUnpublishedCreatedAt()).thenReturn(Optional.empty());

        metrics.refresh();

        assertThat(registry.get("outbox.pending").gauge().value()).isZero();
        assertThat(registry.get("outbox.oldest.age").gauge().value()).isZero();
    }

    @Test
    void countsSendsAndFailures() {
        metrics.recordPublished();
        metrics.recordPublished();
        metrics.recordFailure();

        assertThat(registry.get("outbox.published").counter().count()).isEqualTo(2);
        assertThat(registry.get("outbox.publish.failures").counter().count()).isEqualTo(1);
    }
}
