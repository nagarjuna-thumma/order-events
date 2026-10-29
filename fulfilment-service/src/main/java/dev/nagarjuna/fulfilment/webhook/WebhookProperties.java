package dev.nagarjuna.fulfilment.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("fulfilment.webhooks")
public record WebhookProperties(
        @DefaultValue("12") int maxAttempts,
        @DefaultValue("10s") Duration initialBackoff,
        @DefaultValue("1h") Duration maxBackoff,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("50") int batchSize,
        /* Must be longer than a send can take, or a slow delivery could be picked up twice. */
        @DefaultValue("60s") Duration lease) {
}
