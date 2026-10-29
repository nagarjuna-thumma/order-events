package dev.nagarjuna.fulfilment.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** A partner endpoint that wants to hear about shipments. */
@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id;

    @Column(nullable = false)
    private String url;

    /** Used to sign every payload. Shown once, when the subscription is created. */
    @Column(nullable = false)
    private String secret;

    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    protected WebhookSubscription() {
        // for JPA
    }

    public static WebhookSubscription create(String url, Instant now) {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        WebhookSubscription s = new WebhookSubscription();
        s.id = UUID.randomUUID();
        s.url = url;
        s.secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        s.active = true;
        s.createdAt = now;
        return s;
    }

    public void deactivate() {
        active = false;
    }

    public UUID getId() { return id; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
