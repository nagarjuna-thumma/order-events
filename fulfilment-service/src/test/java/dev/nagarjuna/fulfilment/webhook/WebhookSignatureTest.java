package dev.nagarjuna.fulfilment.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    static final String SECRET = "whsec_test";
    static final String BODY = "{\"type\":\"shipment.created\"}";
    static final Instant SENT = Instant.parse("2026-10-29T12:00:00Z");
    static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void matchesAnHmacComputedWithOpenssl() {
        // echo -n '1793275200.{"type":"shipment.created"}' | openssl dgst -sha256 -hmac whsec_test
        assertThat(WebhookSignature.sign(SECRET, SENT, BODY))
                .isEqualTo("t=1793275200,v1=23356204109e0af4f5ebea48fd4d711c8fad574c3b7caecf38b5a3920745770c");
    }

    @Test
    void receiverAcceptsAGenuineRequest() {
        String header = WebhookSignature.sign(SECRET, SENT, BODY);
        assertThat(WebhookSignature.verify(SECRET, header, BODY, SENT.plusSeconds(3), FIVE_MINUTES)).isTrue();
    }

    @Test
    void changedBodyWrongSecretOrMissingHeaderAreRejected() {
        String header = WebhookSignature.sign(SECRET, SENT, BODY);
        assertThat(WebhookSignature.verify(SECRET, header, BODY.replace("created", "cancelled"), SENT, FIVE_MINUTES)).isFalse();
        assertThat(WebhookSignature.verify("whsec_other", header, BODY, SENT, FIVE_MINUTES)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, null, BODY, SENT, FIVE_MINUTES)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "v1=abc", BODY, SENT, FIVE_MINUTES)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=notanumber,v1=abc", BODY, SENT, FIVE_MINUTES)).isFalse();
    }

    @Test
    void replayedOldRequestIsRejected() {
        String header = WebhookSignature.sign(SECRET, SENT, BODY);
        assertThat(WebhookSignature.verify(SECRET, header, BODY, SENT.plus(Duration.ofMinutes(6)), FIVE_MINUTES)).isFalse();
    }

    @Test
    void forgedTimestampBreaksTheSignature() {
        String header = WebhookSignature.sign(SECRET, SENT, BODY);
        String forged = header.replace("t=" + SENT.getEpochSecond(), "t=" + SENT.plusSeconds(3600).getEpochSecond());
        assertThat(WebhookSignature.verify(SECRET, forged, BODY, SENT.plusSeconds(3600), FIVE_MINUTES)).isFalse();
    }
}
