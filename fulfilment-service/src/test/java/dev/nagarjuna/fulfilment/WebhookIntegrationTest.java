package dev.nagarjuna.fulfilment;

import com.sun.net.httpserver.HttpServer;
import dev.nagarjuna.fulfilment.messaging.KafkaConfig;
import dev.nagarjuna.fulfilment.webhook.WebhookSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A real HTTP receiver on a random port stands in for a partner's endpoint. It answers with
 * whatever status codes the test queues up, and records what it was sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "fulfilment.webhooks.initial-backoff=200ms",
        "fulfilment.webhooks.max-backoff=1s",
        "fulfilment.webhooks.max-attempts=4",
        "fulfilment.webhooks.poll-interval=100ms"})
class WebhookIntegrationTest {

    record Received(String webhookId, String signature, String body) {
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired TestRestTemplate http;

    HttpServer receiver;
    final LinkedBlockingQueue<Integer> responses = new LinkedBlockingQueue<>();
    final Queue<Received> received = new ConcurrentLinkedQueue<>();
    UUID subscriptionId;
    String secret;

    @BeforeEach
    void startReceiverAndSubscribe() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/hooks", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestHeaders().getFirst("X-Webhook-Id"),
                    exchange.getRequestHeaders().getFirst(WebhookSignature.HEADER), body));
            Integer status = responses.poll();
            exchange.sendResponseHeaders(status == null ? 200 : status, -1);
            exchange.close();
        });
        receiver.start();

        Map<?, ?> created = http.postForObject("/webhooks/subscriptions",
                Map.of("url", "http://localhost:" + receiver.getAddress().getPort() + "/hooks"), Map.class);
        subscriptionId = UUID.fromString(created.get("id").toString());
        secret = created.get("secret").toString();
        assertThat(secret).startsWith("whsec_");
    }

    @AfterEach
    void stopReceiver() {
        http.postForObject("/webhooks/subscriptions/" + subscriptionId + "/deactivate", null, Map.class);
        receiver.stop(0);
    }

    private UUID placeOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        kafka.send(KafkaConfig.ORDERS_PLACED, orderId.toString(), """
                {"eventId":"%s","orderId":"%s","customerId":"cust-1","sku":"SKU-7",
                 "quantity":3,"totalAmount":30.00,"occurredAt":"2026-01-01T10:00:00Z"}
                """.formatted(UUID.randomUUID(), orderId)).get();
        return orderId;
    }

    private List<Map<?, ?>> deliveries() {
        return List.of(http.getForObject("/webhooks/subscriptions/" + subscriptionId + "/deliveries", Map[].class));
    }

    @Test
    void failingReceiverIsRetriedUntilItRecoversWithTheSameIdAndAValidSignature() throws Exception {
        responses.addAll(List.of(500, 503));
        UUID orderId = placeOrder();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveries()).singleElement().satisfies(d -> {
                    assertThat(d.get("status")).isEqualTo("DELIVERED");
                    assertThat(d.get("attempts")).isEqualTo(3);
                    assertThat(d.get("lastStatusCode")).isEqualTo(200);
                }));

        assertThat(received).hasSize(3);
        assertThat(received.stream().map(Received::webhookId).distinct()).as("same id on every retry").hasSize(1);
        Received last = received.stream().reduce((a, b) -> b).orElseThrow();
        assertThat(last.body()).contains("\"type\":\"shipment.created\"").contains(orderId.toString()).contains("SKU-7");
        assertThat(WebhookSignature.verify(secret, last.signature(), last.body(), Instant.now(), Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void clientErrorIsNotRetriedButCanBeRedeliveredByHand() throws Exception {
        responses.add(400);
        placeOrder();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveries()).singleElement().satisfies(d -> {
                    assertThat(d.get("status")).isEqualTo("FAILED");
                    assertThat(d.get("attempts")).isEqualTo(1);
                    assertThat(d.get("lastError")).isEqualTo("HTTP 400");
                }));
        String deliveryId = deliveries().get(0).get("id").toString();
        assertThat(http.getForObject("/webhooks/deliveries?status=FAILED", String.class)).contains(deliveryId);

        http.postForObject("/webhooks/deliveries/" + deliveryId + "/redeliver", null, Map.class);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveries()).singleElement().satisfies(d -> assertThat(d.get("status")).isEqualTo("DELIVERED")));
        assertThat(received).hasSize(2);
    }

    @Test
    void receiverThatNeverRecoversEndsUpFailedAfterMaxAttempts() throws Exception {
        responses.addAll(List.of(500, 500, 500, 500, 500));
        placeOrder();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(deliveries()).singleElement().satisfies(d -> {
                    assertThat(d.get("status")).isEqualTo("FAILED");
                    assertThat(d.get("attempts")).isEqualTo(4);
                }));
        Thread.sleep(1500);
        assertThat(received).as("nothing sent after giving up").hasSize(4);
    }
}
