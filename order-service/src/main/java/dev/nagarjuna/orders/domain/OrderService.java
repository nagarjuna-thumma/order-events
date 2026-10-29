package dev.nagarjuna.orders.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nagarjuna.orders.KafkaTopics;
import dev.nagarjuna.orders.outbox.OutboxEvent;
import dev.nagarjuna.orders.outbox.OutboxRepository;
import dev.nagarjuna.orders.tracing.TraceParents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final TraceParents traceParents;

    public OrderService(OrderRepository orders, OutboxRepository outbox, ObjectMapper objectMapper, TraceParents traceParents) {
        this.orders = orders;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.traceParents = traceParents;
    }

    /**
     * Saves the order and its event in one database transaction. Nothing talks to Kafka here;
     * the outbox relay picks the event up afterwards. If Kafka is down, orders still get taken
     * and the events go out once it's back.
     */
    @Transactional
    public Order placeOrder(String customerId, String sku, int quantity, BigDecimal totalAmount) {
        Order order = orders.save(Order.place(customerId, sku, quantity, totalAmount));
        OrderPlacedEvent event = OrderPlacedEvent.from(order);
        outbox.save(OutboxEvent.of(event.eventId(), KafkaTopics.ORDERS_PLACED, order.getId().toString(), toJson(event),
                traceParents.current()));
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findOrder(UUID id) {
        return orders.findById(id);
    }

    private String toJson(OrderPlacedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + event.eventId(), e);
        }
    }
}
