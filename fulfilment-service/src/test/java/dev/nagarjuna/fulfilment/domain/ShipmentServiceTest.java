package dev.nagarjuna.fulfilment.domain;

import dev.nagarjuna.fulfilment.messaging.OrderPlacedEvent;
import dev.nagarjuna.fulfilment.webhook.WebhookPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    ShipmentRepository shipments;

    @Mock
    ProcessedEventRepository processedEvents;

    @Mock
    WebhookPublisher webhooks;

    @InjectMocks
    ShipmentService service;

    private final OrderPlacedEvent event = new OrderPlacedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "cust-1", "SKU-1", 1, new BigDecimal("9.99"), Instant.now());

    @Test
    void newEventCreatesAShipment() {
        when(processedEvents.existsById(event.eventId())).thenReturn(false);
        when(shipments.save(any(Shipment.class))).thenAnswer(returnsFirstArg());

        assertThat(service.handle(event)).isTrue();

        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(shipments).save(any(Shipment.class));
        verify(webhooks).publish(eq("shipment.created"), anyMap());
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processedEvents.existsById(event.eventId())).thenReturn(true);

        assertThat(service.handle(event)).isFalse();

        verify(shipments, never()).save(any());
        verify(webhooks, never()).publish(any(), any());
    }
}
