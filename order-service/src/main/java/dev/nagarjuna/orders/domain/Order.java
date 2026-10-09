package dev.nagarjuna.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String sku;

    private int quantity;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Order() {
        // for JPA
    }

    public static Order place(String customerId, String sku, int quantity, BigDecimal totalAmount) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.sku = sku;
        order.quantity = quantity;
        order.totalAmount = totalAmount;
        order.status = OrderStatus.PLACED;
        order.createdAt = Instant.now();
        return order;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
