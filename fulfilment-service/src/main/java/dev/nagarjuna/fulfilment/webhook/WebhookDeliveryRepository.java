package dev.nagarjuna.fulfilment.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    /** Due deliveries, skipping rows another dispatcher instance has locked. */
    @Query(value = """
            SELECT * FROM webhook_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> lockDue(Instant now, int limit);

    List<WebhookDelivery> findTop50ByStatusOrderByCreatedAtDesc(DeliveryStatus status);

    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAt(UUID subscriptionId);
}
