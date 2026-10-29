package dev.nagarjuna.fulfilment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    public static final String ORDERS_PLACED = "orders.placed";
    public static final String ORDERS_PLACED_DLT = ORDERS_PLACED + ".DLT";

    /**
     * Also declared by order-service, with the same settings. If this service starts first and
     * the topic doesn't exist yet, the consumer would only see it at its next metadata refresh,
     * up to five minutes later. Declaring it here means whichever starts first creates it.
     */
    @Bean
    NewTopic ordersPlacedTopic() {
        return TopicBuilder.name(ORDERS_PLACED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ordersPlacedDeadLetterTopic() {
        return TopicBuilder.name(ORDERS_PLACED_DLT).partitions(1).replicas(1).build();
    }

    /**
     * Retries with exponential backoff, then parks the message on the dead-letter topic so one
     * bad message can't block the partition. Messages that can't even be parsed skip the
     * retries, since retrying won't fix them.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template,
                                          MeterRegistry registry,
                                          @Value("${fulfilment.retry.initial-interval-ms:1000}") long initialInterval,
                                          @Value("${fulfilment.retry.max-attempts:3}") int maxAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(ORDERS_PLACED_DLT, 0));
        // Anything landing on the DLT needs a person to look at it, so it gets its own counter and alert.
        Counter deadLettered = Counter.builder("orders.placed.dead.lettered")
                .description("Order events moved to the dead-letter topic")
                .register(registry);

        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
        backOff.setMaxAttempts(maxAttempts);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            recoverer.accept(record, ex);
            deadLettered.increment();
        }, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
