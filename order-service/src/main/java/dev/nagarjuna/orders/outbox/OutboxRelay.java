package dev.nagarjuna.orders.outbox;

import dev.nagarjuna.orders.tracing.TraceParents;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox table and sends unpublished events to Kafka.
 *
 * Delivery is at-least-once: if the app dies after Kafka accepts a message but before the row
 * is marked published, the message goes out again on the next run. Consumers are expected to
 * de-duplicate on eventId.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxMetrics metrics;
    private final TraceParents traceParents;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, String> kafka,
                       OutboxMetrics metrics,
                       TraceParents traceParents,
                       @Value("${outbox.relay.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.metrics = metrics;
        this.traceParents = traceParents;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval:500ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
        for (OutboxEvent event : batch) {
            // Continue the trace of the request that placed the order. The Kafka producer span
            // becomes a child of this one and adds the traceparent header to the message.
            Span span = traceParents.startChild(event.getTraceParent(), "outbox publish");
            try (Tracer.SpanInScope ignored = traceParents.tracer().withSpan(span)) {
                kafka.send(event.getTopic(), event.getKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
                event.markPublished();
                metrics.recordPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                // Stop here so later events for the same order don't overtake this one. Retried next run.
                span.error(e);
                metrics.recordFailure();
                log.warn("Could not publish outbox event {}, will retry", event.getId(), e);
                return;
            } finally {
                span.end();
            }
        }
    }
}
