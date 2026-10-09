package dev.nagarjuna.fulfilment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nagarjuna.fulfilment.domain.ShipmentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private final ObjectMapper objectMapper;
    private final ShipmentService shipmentService;

    public OrderPlacedListener(ObjectMapper objectMapper, ShipmentService shipmentService) {
        this.objectMapper = objectMapper;
        this.shipmentService = shipmentService;
    }

    @KafkaListener(topics = KafkaConfig.ORDERS_PLACED, groupId = "fulfilment-service")
    public void onOrderPlaced(String payload) throws JsonProcessingException {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        shipmentService.handle(event);
    }
}
