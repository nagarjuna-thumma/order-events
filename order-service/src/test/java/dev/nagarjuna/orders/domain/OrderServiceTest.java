package dev.nagarjuna.orders.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.nagarjuna.orders.outbox.OutboxEvent;
import dev.nagarjuna.orders.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orders;

    @Mock
    OutboxRepository outbox;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, outbox, new ObjectMapper().registerModule(new JavaTimeModule()));
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void placingAnOrderAlsoWritesAnOutboxEventKeyedByOrderId() {
        Order order = service.placeOrder("cust-42", "SKU-1", 2, new BigDecimal("19.98"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        OutboxEvent event = captor.getValue();

        assertThat(event.getTopic()).isEqualTo("orders.placed");
        assertThat(event.getKey()).isEqualTo(order.getId().toString());
        assertThat(event.getPayload())
                .contains(order.getId().toString())
                .contains("\"sku\":\"SKU-1\"")
                .contains("\"quantity\":2");
        assertThat(event.getPublishedAt()).isNull();
    }
}
