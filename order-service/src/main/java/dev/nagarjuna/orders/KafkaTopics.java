package dev.nagarjuna.orders;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String ORDERS_PLACED = "orders.placed";

    @Bean
    NewTopic ordersPlacedTopic() {
        // Keyed by order id, so all events for one order land on the same partition in order.
        return TopicBuilder.name(ORDERS_PLACED).partitions(3).replicas(1).build();
    }
}
