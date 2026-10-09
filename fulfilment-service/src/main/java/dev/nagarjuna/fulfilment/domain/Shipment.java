package dev.nagarjuna.fulfilment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private String sku;

    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Shipment() {
        // for JPA
    }

    public static Shipment pendingFor(UUID orderId, String sku, int quantity) {
        Shipment shipment = new Shipment();
        shipment.id = UUID.randomUUID();
        shipment.orderId = orderId;
        shipment.sku = sku;
        shipment.quantity = quantity;
        shipment.status = ShipmentStatus.PENDING;
        shipment.createdAt = Instant.now();
        return shipment;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public ShipmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
