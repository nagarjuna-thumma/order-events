package dev.nagarjuna.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka when an order is placed. eventId is what consumers use to de-duplicate.
 */
public record OrderPlacedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        String sku,
        int quantity,
        BigDecimal totalAmount,
        Instant occurredAt) {

    public static OrderPlacedEvent from(Order order) {
        return new OrderPlacedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }
}
