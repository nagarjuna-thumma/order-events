package dev.nagarjuna.fulfilment.domain;

import dev.nagarjuna.fulfilment.messaging.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipments;
    private final ProcessedEventRepository processedEvents;

    public ShipmentService(ShipmentRepository shipments, ProcessedEventRepository processedEvents) {
        this.shipments = shipments;
        this.processedEvents = processedEvents;
    }

    /**
     * Kafka can deliver the same event more than once, so this has to be safe to repeat.
     * The shipment and the processed-event marker are written in the same transaction.
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
        shipments.save(Shipment.pendingFor(event.orderId(), event.sku(), event.quantity()));
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return shipments.findByOrderId(orderId);
    }
}
