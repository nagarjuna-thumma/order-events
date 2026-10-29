package dev.nagarjuna.fulfilment.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
class DeliveryNotFoundException extends RuntimeException {

    DeliveryNotFoundException(UUID id) {
        super("No webhook delivery " + id);
    }
}
