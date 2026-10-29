package dev.nagarjuna.fulfilment.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One event for one subscriber. The id goes out as the X-Webhook-Id header and stays the same on
 * every retry, so receivers can drop duplicates the same way our own Kafka consumer does.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Integer lastStatusCode;

    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant deliveredAt;

    protected WebhookDelivery() {
        // for JPA
    }

    static WebhookDelivery pending(UUID id, UUID subscriptionId, String eventType, String payload, Instant now) {
        WebhookDelivery d = new WebhookDelivery();
        d.id = id;
        d.subscriptionId = subscriptionId;
        d.eventType = eventType;
        d.payload = payload;
        d.status = DeliveryStatus.PENDING;
        d.nextAttemptAt = now;
        d.createdAt = now;
        return d;
    }

    /** Pushes the next attempt out so no other dispatcher picks this row up while it is being sent. */
    void lease(Instant until) {
        nextAttemptAt = until;
    }

    void delivered(int statusCode, Instant now) {
        attempts++;
        status = DeliveryStatus.DELIVERED;
        lastStatusCode = statusCode;
        lastError = null;
        deliveredAt = now;
    }

    void failedAttempt(Integer statusCode, String error, Instant retryAt) {
        attempts++;
        lastStatusCode = statusCode;
        lastError = error == null || error.length() <= 500 ? error : error.substring(0, 500);
        if (retryAt == null) {
            status = DeliveryStatus.FAILED;
        } else {
            nextAttemptAt = retryAt;
        }
    }

    /** Manual replay after the receiver is fixed. Starts a fresh round of retries. */
    void redeliver(Instant now) {
        status = DeliveryStatus.PENDING;
        attempts = 0;
        nextAttemptAt = now;
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
