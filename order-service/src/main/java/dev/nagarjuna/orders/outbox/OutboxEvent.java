package dev.nagarjuna.orders.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private String traceParent;

    protected OutboxEvent() {
        // for JPA
    }

    public static OutboxEvent of(UUID id, String topic, String key, String payload, String traceParent) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.topic = topic;
        event.key = key;
        event.payload = payload;
        event.traceParent = traceParent;
        event.createdAt = Instant.now();
        return event;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getKey() { return key; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getTraceParent() { return traceParent; }
}
