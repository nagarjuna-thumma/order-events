package dev.nagarjuna.fulfilment.api;

import dev.nagarjuna.fulfilment.domain.Shipment;
import dev.nagarjuna.fulfilment.domain.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentResponse> getByOrder(@PathVariable UUID orderId) {
        return shipmentService.findByOrderId(orderId)
                .map(ShipmentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record ShipmentResponse(UUID id, UUID orderId, String sku, int quantity, String status, Instant createdAt) {
        static ShipmentResponse from(Shipment s) {
            return new ShipmentResponse(s.getId(), s.getOrderId(), s.getSku(), s.getQuantity(),
                    s.getStatus().name(), s.getCreatedAt());
        }
    }
}
