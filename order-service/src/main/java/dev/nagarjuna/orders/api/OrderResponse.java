package dev.nagarjuna.orders.api;

import dev.nagarjuna.orders.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        String sku,
        int quantity,
        BigDecimal totalAmount,
        String status,
        Instant createdAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getSku(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt());
    }
}
