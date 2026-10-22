package dev.nagarjuna.fulfilment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nagarjuna.fulfilment.domain.ShipmentService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private final ObjectMapper objectMapper;
    private final ShipmentService shipmentService;
    private final Counter processed;
    private final Counter duplicates;

    public OrderPlacedListener(ObjectMapper objectMapper, ShipmentService shipmentService, MeterRegistry registry) {
        this.objectMapper = objectMapper;
        this.shipmentService = shipmentService;
        this.processed = Counter.builder("orders.placed.processed")
                .description("Order events that created a shipment")
                .register(registry);
        this.duplicates = Counter.builder("orders.placed.duplicates")
                .description("Order events skipped because they had already been handled")
                .register(registry);
    }

    @KafkaListener(topics = KafkaConfig.ORDERS_PLACED, groupId = "fulfilment-service")
    public void onOrderPlaced(String payload) throws JsonProcessingException {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        if (shipmentService.handle(event)) {
            processed.increment();
        } else {
            duplicates.increment();
        }
    }
}
