# order-events

[![build](https://github.com/nagarjuna-thumma/order-events/actions/workflows/build.yml/badge.svg)](https://github.com/nagarjuna-thumma/order-events/actions/workflows/build.yml)

Two small Spring Boot services that talk through Kafka. The order service takes orders and the fulfilment service creates a shipment for each one.

The business logic is deliberately thin. I built this to show the parts of event-driven systems that usually go wrong in production: losing events when the broker is down, processing the same message twice, and one bad message blocking a whole partition.

```mermaid
flowchart LR
    client([client]) -->|POST /orders| os[order-service]
    os -->|one transaction| db1[(orders + outbox)]
    relay[outbox relay] -->|polls| db1
    relay -->|orders.placed| kafka{{Kafka}}
    kafka --> fs[fulfilment-service]
    fs -->|one transaction| db2[(shipments + processed_events)]
    fs -.->|after retries| dlt{{orders.placed.DLT}}
```

## What's in it

**Transactional outbox (order-service).** Placing an order writes the order and its event to Postgres in one transaction. A scheduled relay then sends unpublished events to Kafka and marks them sent. If Kafka is down, orders are still accepted and the events go out once it's back. The relay uses `FOR UPDATE SKIP LOCKED`, so you can run more than one instance without double-sending.

**Idempotent consumer (fulfilment-service).** The outbox gives at-least-once delivery, so duplicates are expected. Each handled event id is stored in `processed_events` in the same transaction as the shipment, and repeats are skipped. The primary key on that table is the real guard. The `existsById` check just avoids a pointless insert.

**Retries and a dead-letter topic.** Failed messages are retried with exponential backoff, then moved to `orders.placed.DLT`. Messages that can't be parsed go straight to the DLT, because retrying won't fix them.

**Ordering.** Events are keyed by order id, so everything about one order stays on one partition and in order. The relay stops at the first failed send in a batch for the same reason.

## Running it

You need Java 21 and Docker.

```bash
docker compose up -d                      # Postgres and Kafka
mvn -pl order-service spring-boot:run      # port 8080
mvn -pl fulfilment-service spring-boot:run # port 8081, in another terminal
```

Place an order, then look up its shipment:

```bash
curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","sku":"BOOK-123","quantity":2,"totalAmount":39.98}'

curl -s localhost:8081/shipments/<order id from the response>
```

## Tests

```bash
mvn verify
```

Unit tests use JUnit 5 and Mockito. The integration tests start real Postgres and Kafka with Testcontainers, so they need Docker. They check that:

- an order placed over HTTP ends up as a message on `orders.placed`
- the same event delivered twice creates only one shipment
- an unreadable message lands on the dead-letter topic

GitHub Actions runs the full suite on every push.

## Stack

Java 21 · Spring Boot 3.5 · Spring Kafka · Spring Data JPA · PostgreSQL · Flyway · Testcontainers · Docker Compose · GitHub Actions

## What I'd add next

- Avro or JSON Schema with a schema registry instead of each service keeping its own copy of the event
- A cleanup job for old published outbox rows and old `processed_events` rows
- Micrometer metrics for outbox lag and DLT volume, with an alert on both
- Debezium CDC as an alternative to the polling relay
