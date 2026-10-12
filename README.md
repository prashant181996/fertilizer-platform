# Fertilizer Order Management Platform

A runnable Java 21 / Spring Boot microservices demonstration for fertilizer catalog, stock reservation, order orchestration, simulated payment authorization, delivery tracking, and Kafka-driven notification events.

> **Scope / performance note:** this is a working local-development baseline, not a certified production deployment. It has not been benchmarked at 10,000 TPS. The payment and delivery services are demos, notification logs events instead of sending real SMS/email, and the order workflow does not yet use a transactional outbox or a durable Saga. Do not use the included development credentials in production.

## Services

| Service | Port | Main endpoints |
|---|---:|---|
| API Gateway | 8080 | Gateway routes; Redis-backed rate limit on product route |
| Product | 8081 | `GET /api/products`, `GET /api/products/{id}`, `GET /api/products/sku/{sku}` |
| Inventory | 8082 | `GET /api/inventory/{productId}`, `POST /api/inventory/reservations`, `POST /api/inventory/release` |
| Order | 8083 | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}` |
| Payment | 8084 | `POST /api/payments/authorize`, `GET /api/payments/{reference}` |
| Delivery | 8085 | `POST /api/deliveries`, `GET /api/deliveries/{orderId}` |
| Notification | 8086 | `POST /api/notifications`, `GET /api/notifications/health`; consumes `orders.created` |

## Requirements

- Docker Desktop with Docker Compose
- For running services directly: Java 21 and Maven 3.9+

## Run everything with Docker

From this directory:

```bash
docker compose up --build -d
docker compose ps
```

First startup builds all seven services and starts PostgreSQL, Redis and Kafka. Give the application services a minute to initialize. Check a service with `curl http://localhost:8081/actuator/health`.

Stop the stack:

```bash
docker compose down
```

Remove development database data too (destructive):

```bash
docker compose down -v
```

## Try it

List seeded products:

```bash
curl http://localhost:8080/api/products
```

Create an order. Product IDs 1, 2 and 3 are seeded on first startup:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dealer-demo-001' \
  -H 'Idempotency-Key: demo-order-0001' \
  -d '{"customerId":"dealer-100","productId":1,"quantity":2}'
```

The order service looks up the product, reserves stock under a database row lock, authorizes a simulated payment, stores the order, and publishes an `orders.created` event. The notification service logs consumed events. Query stock and orders:

```bash
curl http://localhost:8080/api/inventory/1
curl http://localhost:8080/api/orders
```

Create a delivery for an order ID returned by the order endpoint:

```bash
curl -X POST http://localhost:8080/api/deliveries -H 'Content-Type: application/json' -d '{"orderId":"REPLACE_WITH_ORDER_ID","address":"Farm, Pune, Maharashtra"}'
```

## Rate limiting

The gateway's product and order routes use Redis `RequestRateLimiter`: replenish rate 100 requests/second per key and burst capacity 200. It uses `X-API-Key` when present, otherwise the remote client IP. The example only rate-limits the product route to keep the demonstration explicit; configure matching filters for other routes before exposing them. Do not trust arbitrary API keys without authenticating them. A 429 response indicates the key has exceeded the configured limit.

These are per-key starter values, not a guarantee of 10,000 TPS. 10,000 TPS must be demonstrated by load testing the full order path, with a defined latency SLO, payload, mix, failure rate, data size, hardware and deployment topology. Scale gateway replicas, Redis, PostgreSQL, Kafka partitions/consumers and downstream services based on measured bottlenecks.

## Important engineering work before production

- Add authentication/authorization (OAuth2/JWT), API-key issuance and secrets management.
- Add transactional outbox + idempotent Kafka consumers and a durable Saga with compensation/reconciliation.
- Persist payment/delivery state; integrate a real payment provider and SMS/email provider.
- Add request timeouts, retries with backoff, circuit breakers, tracing, metrics dashboards, audit logs and alerting.
- Separate databases/credentials by service, configure migrations (Flyway), backups, TLS, network policies, resource limits and production secrets.
- Add integration tests and load-test with k6/Gatling before making throughput claims.

## Build

```bash
mvn clean package
```

The parent POM is a Maven multi-module build. All modules target Java 21.
