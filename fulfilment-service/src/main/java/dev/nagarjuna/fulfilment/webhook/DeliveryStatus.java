package dev.nagarjuna.fulfilment.webhook;

public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    /** Gave up: retries ran out, or the receiver said the request itself was wrong. */
    FAILED
}
