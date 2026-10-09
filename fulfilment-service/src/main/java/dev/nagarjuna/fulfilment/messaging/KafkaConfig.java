package dev.nagarjuna.fulfilment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
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
                                          @Value("${fulfilment.retry.initial-interval-ms:1000}") long initialInterval,
                                          @Value("${fulfilment.retry.max-attempts:3}") int maxAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(ORDERS_PLACED_DLT, 0));

        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
        backOff.setMaxAttempts(maxAttempts);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
