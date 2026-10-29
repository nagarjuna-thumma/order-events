package dev.nagarjuna.fulfilment.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Queues an event for every active subscriber. Must run inside the caller's transaction: the
 * deliveries are saved together with the change they describe, so a webhook is never sent for a
 * shipment that rolled back, and never lost for one that committed. Same idea as the outbox in
 * order-service.
 */
@Component
public class WebhookPublisher {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookPublisher(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries,
                            ObjectMapper objectMapper, Clock clock) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String eventType, Object data) {
        Instant now = clock.instant();
        for (WebhookSubscription sub : subscriptions.findByActiveTrue()) {
            UUID id = UUID.randomUUID();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", id);
            envelope.put("type", eventType);
            envelope.put("createdAt", now);
            envelope.put("data", data);
            deliveries.save(WebhookDelivery.pending(id, sub.getId(), eventType, toJson(envelope), now));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook payload can't be serialised", e);
        }
    }
}
