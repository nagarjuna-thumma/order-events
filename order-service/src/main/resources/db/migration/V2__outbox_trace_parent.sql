-- W3C traceparent of the request that created the event, so the relay can continue the same trace.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
