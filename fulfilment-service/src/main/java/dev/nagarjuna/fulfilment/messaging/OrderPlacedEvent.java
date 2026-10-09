package dev.nagarjuna.fulfilment.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This service's own copy of the event contract. Unknown fields are ignored so the
 * order service can add fields without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        String sku,
        int quantity,
        BigDecimal totalAmount,
        Instant occurredAt) {
}
