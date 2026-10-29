package dev.nagarjuna.fulfilment.webhook;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Polls for due deliveries and POSTs them. Safe to run on several instances: rows are claimed
 * with SKIP LOCKED and leased, so each delivery attempt goes out from one place.
 */
@Component
class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookDeliveryStore store;
    private final RestClient http;
    private final Clock clock;
    private final MeterRegistry meters;

    WebhookDispatcher(WebhookDeliveryStore store, WebhookProperties props, Clock clock, MeterRegistry meters) {
        this.store = store;
        this.clock = clock;
        this.meters = meters;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.timeout()).build());
        factory.setReadTimeout(props.timeout());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${fulfilment.webhooks.poll-interval:1s}")
    void dispatch() {
        for (WebhookDeliveryStore.Claimed d : store.claimDue()) {
            send(d);
        }
    }

    private void send(WebhookDeliveryStore.Claimed d) {
        Integer status = null;
        String error = null;
        try {
            status = http.post().uri(d.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Webhook-Id", d.id().toString())
                    .header(WebhookSignature.HEADER, WebhookSignature.sign(d.secret(), clock.instant(), d.payload()))
                    .body(d.payload())
                    .exchange((request, response) -> response.getStatusCode().value());
        } catch (RestClientException e) {
            error = e.getMessage(); // timeout, connection refused, DNS
        }

        if (status != null && status >= 200 && status < 300) {
            store.recordSuccess(d.id(), status);
            count("delivered");
            return;
        }
        if (error == null) error = "HTTP " + status;
        boolean willRetry = store.recordFailure(d.id(), status, error);
        count(willRetry ? "retry" : "failed");
        if (willRetry) {
            log.info("Webhook {} to {} failed on attempt {} ({}), will retry", d.id(), d.url(), d.attemptsSoFar() + 1, error);
        } else {
            log.warn("Webhook {} to {} gave up after attempt {} ({})", d.id(), d.url(), d.attemptsSoFar() + 1, error);
        }
    }

    private void count(String outcome) {
        meters.counter("webhook.deliveries", "outcome", outcome).increment();
    }
}
