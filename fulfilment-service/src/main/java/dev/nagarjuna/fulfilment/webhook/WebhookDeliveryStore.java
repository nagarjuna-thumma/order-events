package dev.nagarjuna.fulfilment.webhook;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The short database transactions around a send. Rows are claimed and leased in one transaction
 * and the result recorded in another, so no row lock or connection is held while waiting on a
 * slow receiver.
 */
@Component
class WebhookDeliveryStore {

    record Claimed(UUID id, String url, String secret, String payload, int attemptsSoFar, String traceParent) {
    }

    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookProperties props;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    WebhookDeliveryStore(WebhookDeliveryRepository deliveries, WebhookSubscriptionRepository subscriptions,
                         WebhookProperties props, Clock clock) {
        this.deliveries = deliveries;
        this.subscriptions = subscriptions;
        this.props = props;
        this.clock = clock;
        this.retryPolicy = new RetryPolicy(props.maxAttempts(), props.initialBackoff(), props.maxBackoff(),
                bound -> ThreadLocalRandom.current().nextLong(bound));
    }

    @Transactional
    public List<Claimed> claimDue() {
        Instant now = clock.instant();
        return deliveries.lockDue(now, props.batchSize()).stream().map(d -> {
            d.lease(now.plus(props.lease()));
            WebhookSubscription sub = subscriptions.findById(d.getSubscriptionId()).orElseThrow();
            return new Claimed(d.getId(), sub.getUrl(), sub.getSecret(), d.getPayload(), d.getAttempts(), d.getTraceParent());
        }).toList();
    }

    @Transactional
    public void recordSuccess(UUID id, int statusCode) {
        deliveries.findById(id).orElseThrow().delivered(statusCode, clock.instant());
    }

    /** @return true if another attempt is scheduled, false if the delivery is now FAILED */
    @Transactional
    public boolean recordFailure(UUID id, Integer statusCode, String error) {
        WebhookDelivery d = deliveries.findById(id).orElseThrow();
        boolean retry = statusCode == null || RetryPolicy.retryable(statusCode);
        Instant retryAt = retry ? retryPolicy.nextAttempt(d.getAttempts() + 1, clock.instant()) : null;
        d.failedAttempt(statusCode, error, retryAt);
        return retryAt != null;
    }

    @Transactional
    public WebhookDelivery redeliver(UUID id) {
        WebhookDelivery d = deliveries.findById(id).orElseThrow(() -> new DeliveryNotFoundException(id));
        d.redeliver(clock.instant());
        return d;
    }
}
