package dev.nagarjuna.fulfilment.domain;

import dev.nagarjuna.fulfilment.messaging.OrderPlacedEvent;
import dev.nagarjuna.fulfilment.webhook.WebhookPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipments;
    private final ProcessedEventRepository processedEvents;
    private final WebhookPublisher webhooks;

    public ShipmentService(ShipmentRepository shipments, ProcessedEventRepository processedEvents, WebhookPublisher webhooks) {
        this.shipments = shipments;
        this.processedEvents = processedEvents;
        this.webhooks = webhooks;
    }

    /**
     * Kafka can deliver the same event more than once, so this has to be safe to repeat.
     * The shipment, the processed-event marker and the outgoing webhooks are written in the same transaction.
     *
     * @return true if a shipment was created, false if the event had already been handled
     */
    @Transactional
    public boolean handle(OrderPlacedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Skipping duplicate event {} for order {}", event.eventId(), event.orderId());
            return false;
        }
        processedEvents.save(new ProcessedEvent(event.eventId()));
        Shipment shipment = shipments.save(Shipment.pendingFor(event.orderId(), event.sku(), event.quantity()));
        webhooks.publish("shipment.created", Map.of(
                "shipmentId", shipment.getId(),
                "orderId", shipment.getOrderId(),
                "sku", shipment.getSku(),
                "quantity", shipment.getQuantity(),
                "status", shipment.getStatus().name()));
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return shipments.findByOrderId(orderId);
    }
}
