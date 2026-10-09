package dev.nagarjuna.orders;

import dev.nagarjuna.orders.api.CreateOrderRequest;
import dev.nagarjuna.orders.api.OrderResponse;
import dev.nagarjuna.orders.outbox.OutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class OrderFlowIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    KafkaConnectionDetails kafka;

    @Test
    void placedOrderIsStoredAndItsEventReachesKafka() {
        var request = new CreateOrderRequest("cust-7", "SKU-42", 3, new BigDecimal("59.97"));

        ResponseEntity<OrderResponse> created = http.postForEntity("/orders", request, OrderResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID orderId = created.getBody().id();

        ResponseEntity<OrderResponse> fetched = http.getForEntity("/orders/" + orderId, OrderResponse.class);
        assertThat(fetched.getBody().status()).isEqualTo("PLACED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outbox.countByPublishedAtIsNull()).isZero());

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
                    assertThat(record.value()).contains("\"sku\":\"SKU-42\"");
                });
            });
        }
    }

    @Test
    void invalidOrderIsRejected() {
        var request = new CreateOrderRequest("", "SKU-42", 0, new BigDecimal("10.00"));

        ResponseEntity<String> response = http.postForEntity("/orders", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
