package dev.nagarjuna.fulfilment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per event we've handled. The primary key is the event id, so the database itself
 * refuses to record the same event twice.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // for JPA
    }

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
}
