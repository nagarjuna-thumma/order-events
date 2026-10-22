package dev.nagarjuna.fulfilment;

import dev.nagarjuna.fulfilment.domain.ShipmentRepository;
import dev.nagarjuna.fulfilment.messaging.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "fulfilment.retry.initial-interval-ms=100")
class FulfilmentIntegrationTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ShipmentRepository shipments;

    @Autowired
    KafkaConnectionDetails kafkaConnection;

    @Autowired
    MeterRegistry meters;

    @Test
    void sameEventDeliveredTwiceCreatesOneShipment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String event = """
                {"eventId":"%s","orderId":"%s","customerId":"cust-1","sku":"SKU-9",
                 "quantity":2,"totalAmount":20.00,"occurredAt":"2026-01-01T10:00:00Z"}
                """.formatted(UUID.randomUUID(), orderId);

        kafka.send(KafkaConfig.ORDERS_PLACED, orderId.toString(), event).get();
        kafka.send(KafkaConfig.ORDERS_PLACED, orderId.toString(), event).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(shipments.countByOrderId(orderId)).isEqualTo(1));

        // Give the duplicate time to be consumed, then check it really was ignored.
        Thread.sleep(2000);
        assertThat(shipments.countByOrderId(orderId)).isEqualTo(1);
        assertThat(meters.get("orders.placed.duplicates").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unreadableMessageGoesToDeadLetterTopic() throws Exception {
        String key = "broken-" + UUID.randomUUID();
        kafka.send(KafkaConfig.ORDERS_PLACED, key, "this is not json").get();

        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(KafkaConfig.ORDERS_PLACED_DLT));

            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).anySatisfy(record -> assertThat(record.key()).isEqualTo(key));
            });
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(meters.get("orders.placed.dead.lettered").counter().count()).isGreaterThanOrEqualTo(1));
        }
    }
}
