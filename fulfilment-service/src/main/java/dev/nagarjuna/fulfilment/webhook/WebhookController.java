package dev.nagarjuna.fulfilment.webhook;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks")
class WebhookController {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryStore store;
    private final Clock clock;

    WebhookController(WebhookSubscriptionRepository subscriptions, WebhookDeliveryRepository deliveries,
                      WebhookDeliveryStore store, Clock clock) {
        this.subscriptions = subscriptions;
        this.deliveries = deliveries;
        this.store = store;
        this.clock = clock;
    }

    record CreateSubscription(@NotBlank @Pattern(regexp = "https?://.+") String url) {
    }

    /** The only response that includes the signing secret. */
    record CreatedSubscription(UUID id, String url, String secret, Instant createdAt) {
    }

    record SubscriptionView(UUID id, String url, boolean active, Instant createdAt) {
        static SubscriptionView from(WebhookSubscription s) {
            return new SubscriptionView(s.getId(), s.getUrl(), s.isActive(), s.getCreatedAt());
        }
    }

    record DeliveryView(UUID id, UUID subscriptionId, String eventType, String status, int attempts,
                        Integer lastStatusCode, String lastError, Instant nextAttemptAt, Instant deliveredAt) {
        static DeliveryView from(WebhookDelivery d) {
            return new DeliveryView(d.getId(), d.getSubscriptionId(), d.getEventType(), d.getStatus().name(),
                    d.getAttempts(), d.getLastStatusCode(), d.getLastError(), d.getNextAttemptAt(), d.getDeliveredAt());
        }
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedSubscription subscribe(@Valid @RequestBody CreateSubscription request) {
        WebhookSubscription s = subscriptions.save(WebhookSubscription.create(request.url(), clock.instant()));
        return new CreatedSubscription(s.getId(), s.getUrl(), s.getSecret(), s.getCreatedAt());
    }

    @GetMapping("/subscriptions")
    List<SubscriptionView> listSubscriptions() {
        return subscriptions.findAll().stream().map(SubscriptionView::from).toList();
    }

    @PostMapping("/subscriptions/{id}/deactivate")
    SubscriptionView deactivate(@PathVariable UUID id) {
        WebhookSubscription s = subscriptions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No subscription " + id));
        s.deactivate();
        return SubscriptionView.from(subscriptions.save(s));
    }

    @GetMapping("/subscriptions/{id}/deliveries")
    List<DeliveryView> deliveriesFor(@PathVariable UUID id) {
        return deliveries.findBySubscriptionIdOrderByCreatedAt(id).stream().map(DeliveryView::from).toList();
    }

    /** For an operator: what's stuck or gave up. */
    @GetMapping("/deliveries")
    List<DeliveryView> byStatus(@RequestParam DeliveryStatus status) {
        return deliveries.findTop50ByStatusOrderByCreatedAtDesc(status).stream().map(DeliveryView::from).toList();
    }

    @PostMapping("/deliveries/{id}/redeliver")
    DeliveryView redeliver(@PathVariable UUID id) {
        return DeliveryView.from(store.redeliver(id));
    }
}
