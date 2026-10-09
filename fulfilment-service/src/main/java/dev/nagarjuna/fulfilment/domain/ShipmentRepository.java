package dev.nagarjuna.fulfilment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);

    long countByOrderId(UUID orderId);
}
