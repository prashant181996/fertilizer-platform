# Fertilizer Order Management Platform

Java 21 / Spring Boot 3.4 microservices starter for fertilizer catalog, inventory, order intake, simulated payment, delivery and Kafka-driven notifications.

> **Honest capacity statement:** this repository is configured for performance testing and documents a path to 10,000 TPS. It has NOT been benchmarked or certified to sustain 10,000 TPS. The current order request still performs synchronous product, inventory and payment calls, so this baseline should not be described as proven for that throughput. See `docs/10k-tps-and-data-ownership.md` for the required scale-out architecture.

## Services

| Service | Port | Owns / provides |
|---|---:|---|
| API Gateway | 8080 | Routing and Redis-backed per-client limits |
| Product | 8081 | Fertilizer catalog; owns its H2 file database `products` |
| Inventory | 8082 | Stock and reservations; owns its H2 file database `inventory` |
| Order | 8083 | Order records and idempotency keys; owns its H2 file database `orders` |
| Payment | 8084 | Demo payment authorization (in-memory only; not production durable) |
| Delivery | 8085 | Demo shipment records (in-memory only; not production durable) |
| Notification | 8086 | Consumes order events and logs notifications |

## Database choice: H2

Product, inventory and order services each use a separate file-based H2 database, persisted in its own Docker named volume. Payment and delivery have H2 configured for future JPA persistence, but their current demo endpoints still use in-memory state. H2 is intended for local development and functional testing; it is not appropriate as the database tier for a production 10,000 TPS deployment. Each domain remains the sole owner of its own data; do not share H2 files between services. H2 console is disabled by default.

## Run locally

Requirements: Docker Desktop + Compose. Run from this directory:

```bash
docker compose up --build -d
docker compose ps
```

The first run creates each H2 database automatically. Docker named volumes preserve database files across container restarts. `docker compose down -v` deletes those volumes and all local database data.

## Try it

```bash
curl http://localhost:8080/api/products
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dealer-demo-001' \
  -H 'Idempotency-Key: demo-order-0001' \
  -d '{"customerId":"dealer-100","productId":1,"quantity":2}'
```

A repeated `Idempotency-Key` returns the existing order instead of intentionally creating another order. This reduces duplicate effects but does not replace end-to-end idempotency in inventory/payment and durable Saga recovery.


## End-to-end order request flow

The diagram below reflects the current implementation. The HTTP order path is synchronous through product lookup, inventory reservation, and simulated payment authorization. Kafka notification is attempted after the order is confirmed. Delivery is a separate demo API and is not automatically invoked by order creation.

```mermaid
flowchart TD
    A[Dealer / Client] -->|POST /api/orders + API key + Idempotency-Key| B[API Gateway :8080]
    B --> C{Redis rate limit allows request?}
    C -- No --> R[HTTP 429 Too Many Requests]
    C -- Yes --> D[Order Service :8083]
    D --> E{Validate customer, product ID, quantity, key}
    E -- Invalid --> V[HTTP 400 Bad Request]
    E -- Valid --> F{Idempotency key already exists?}
    F -- Yes --> G[Return existing order]
    F -- No --> H[Persist PROCESSING order in Orders H2]
    H --> I[GET product details]
    I --> J[Product Service :8081 / Products H2]
    J --> K{Product lookup successful?}
    K -- No --> K1[Mark order FAILED; return gateway error]
    K -- Yes --> L[Calculate total from product price × quantity]
    L --> M[POST inventory reservation]
    M --> N[Inventory Service :8082 / Inventory H2]
    N --> O{Stock available and reservation succeeds?}
    O -- No --> O1[Mark order FAILED; return HTTP 409]
    O -- Yes --> P[POST payment authorization]
    P --> Q[Payment Service :8084 / simulated authorization]
    Q --> S{Payment succeeds?}
    S -- No --> S1[Attempt inventory release; mark order FAILED]
    S1 --> S2[Return payment error]
    S -- Yes --> T[Save order as CONFIRMED in Orders H2]
    T --> U[Publish orders.created to Kafka]
    U --> W[Notification Service :8086 consumes event]
    W --> X[Write demo notification to service logs]
    T --> Y[Return HTTP 201 + order response to client]
    U -. Publish is best-effort in current code .-> Z[If publish fails, order may still be confirmed]

    style R fill:#ffe3e3,stroke:#c62828,color:#222
    style V fill:#ffe3e3,stroke:#c62828,color:#222
    style K1 fill:#ffe3e3,stroke:#c62828,color:#222
    style O1 fill:#ffe3e3,stroke:#c62828,color:#222
    style S1 fill:#ffe3e3,stroke:#c62828,color:#222
    style X fill:#e4f5e8,stroke:#2e7d32,color:#222
```

### Sequence view

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant G as API Gateway
    participant R as Redis
    participant O as Order Service
    participant P as Product Service
    participant I as Inventory Service
    participant Pay as Payment Service
    participant K as Kafka
    participant N as Notification Service

    C->>G: POST /api/orders (API key, Idempotency-Key)
    G->>R: Check rate limit
    R-->>G: Allow / reject
    G->>O: Forward order request
    O->>O: Validate and check idempotency key
    O->>O: Persist PROCESSING order
    O->>P: GET /api/products/{productId}
    P-->>O: Product details and price
    O->>I: POST /api/inventory/reservations
    I-->>O: Reservation result
    O->>Pay: POST /api/payments/authorize
    Pay-->>O: Simulated authorization result
    alt Payment authorized
        O->>O: Persist CONFIRMED order
        O-)K: Send orders.created event (best effort)
        O-->>G: 201 Created + order
        G-->>C: 201 Created + order
        K-->>N: Deliver orders.created event
        N->>N: Log demo notification
    else Payment failed
        O->>I: POST /api/inventory/release (compensation attempt)
        O->>O: Mark order FAILED
        O-->>G: Error response
        G-->>C: Error response
    end
```

## Single source of truth and service data ownership

- **Product Service** is authoritative for product catalog and price.
- **Inventory Service** is authoritative for stock and reservations.
- **Order Service** is authoritative for order state and idempotency keys.
- **Payment Service** and **Delivery Service** currently use in-memory demo state; durable persistence is not yet implemented even though H2 configuration is present.
- **Redis** stores rate-limit state; it is not the source of truth for orders or inventory.
- **Kafka** transports events; messages and consumer state are not substitutes for the owning service's business database.

Services must access another domain through its API or events, not by opening another service's H2 database file. The current order workflow uses best-effort compensation and best-effort Kafka publication; use a durable Saga plus transactional outbox and end-to-end idempotency before production use.

## Rate limiting and load test

The gateway rate limiter is per API key: 100 tokens/sec with a burst of 200 in the starter config. Aggregate throughput depends on gateway replicas, Redis capacity, API-key distribution, downstream services, and the request mix. Do not simply set one client key to 10,000 TPS.

Use k6 from a machine with k6 installed. Begin with a low rate and increase gradually:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e RATE=100 -e DURATION=1m load-test/k6-orders.js
```

A 10,000 request/sec test should be run against a production-like multi-node environment, with a separate load generator, sufficient distinct authenticated clients, pre-provisioned stock, and defined latency/error SLOs. The included local Compose stack is for functional development, not a 10k TPS proof.

## Production gaps

- Replace synchronous orchestration with durable asynchronous acceptance + transactional outbox + Kafka consumers + Saga state machine and compensation.
- Persist payment and delivery state; the included implementations are in-memory demos.
- Add service-specific DB users/credentials, Flyway migrations, backup/restore, TLS, secrets management, OAuth2/JWT, authorization, audit logs and observability.
- Add idempotency to reservation/release/payment operations, including durable unique keys.
- Use a multi-broker Kafka cluster (replication factor 3), appropriate partitions, and monitor lag.
- Load-test and tune pools, indexes, partitioning, gateway replicas, Redis and consumer concurrency based on measurements.
