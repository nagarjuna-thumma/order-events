-- W3C traceparent of the Kafka message that caused the delivery, so the HTTP call joins the same trace.
ALTER TABLE webhook_deliveries ADD COLUMN trace_parent VARCHAR(55);
