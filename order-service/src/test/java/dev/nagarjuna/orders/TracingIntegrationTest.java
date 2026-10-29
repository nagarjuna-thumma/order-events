package dev.nagarjuna.orders;

import dev.nagarjuna.orders.api.CreateOrderRequest;
import dev.nagarjuna.orders.api.OrderResponse;
import dev.nagarjuna.orders.outbox.OutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The outbox breaks the call chain: the request commits a row and returns, and a scheduled relay
 * sends it later on another thread. This checks the trace survives that gap and reaches Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.otlp.tracing.export.enabled=false")
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureObservability
class TracingIntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired OutboxRepository outbox;
    @Autowired KafkaConnectionDetails kafka;

    @Test
    void traceFromTheIncomingRequestIsCarriedThroughTheOutboxOntoTheKafkaMessage() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01");

        ResponseEntity<OrderResponse> created = http.postForEntity("/orders",
                new HttpEntity<>(new CreateOrderRequest("cust-9", "SKU-T", 1, new BigDecimal("5.00")), headers),
                OrderResponse.class);
        UUID orderId = created.getBody().id();

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(KafkaTopics.ORDERS_PLACED));

            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).anySatisfy(record -> {
                    assertThat(record.key()).isEqualTo(orderId.toString());
                    Header traceparent = record.headers().lastHeader("traceparent");
                    assertThat(traceparent).isNotNull();
                    assertThat(new String(traceparent.value(), StandardCharsets.UTF_8)).startsWith("00-" + traceId + "-");
                });
            });
        }
    }
}
