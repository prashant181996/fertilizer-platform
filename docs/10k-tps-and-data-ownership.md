# 10,000 TPS and Single Source of Truth

## What “single source of truth” means here

Do not let multiple services independently write the same business fact. Each domain has one authoritative owner and its own write database:

- **Orders:** Order Service / `fertilizer_orders`. Order status, customer reference, quantity, accepted price snapshot and idempotency key live here.
- **Catalog and price:** Product Service / `fertilizer_products`. The order may store a price snapshot for the transaction, but only Product Service changes catalog pricing.
- **Available/reserved stock:** Inventory Service / `fertilizer_inventory`. No other service updates stock directly. Reservation commands must be idempotent and atomic.
- **Payments:** Payment Service must own durable payment intents/authorizations in a dedicated database (not implemented in this demo; current map is in-memory).
- **Delivery:** Delivery Service must own durable shipment state (not implemented in this demo; current map is in-memory).

Kafka events are immutable facts/change notifications, not a second writable master. Consumers may keep rebuildable projections/caches, clearly treated as derived data. Include event IDs, aggregate IDs, schema versions and timestamps; make consumers idempotent.

The local Compose setup uses one H2 for local development (PostgreSQL or another production database for deployment) container but separate logical databases for order/product/inventory. That provides logical ownership for development only. Production should use separate service credentials and preferably independently managed databases or clusters as required by isolation/availability goals. Never let one service write another service's tables.

## Architecture required to target 10k TPS

The current demo's order endpoint calls Product, Inventory and Payment synchronously. That multiplies network hops and holds request threads during downstream latency. It is **not** a demonstrated 10k TPS design. Move the hot path to durable asynchronous acceptance:

1. Gateway authenticates the client, enforces per-client quotas and a separate aggregate admission-control budget.
2. Order Service validates request shape, claims a unique idempotency key and commits an `ACCEPTED/PENDING` order plus an outbox event in the same database transaction. Return `202 Accepted` with order ID/status; do not claim the order is confirmed yet.
3. An outbox publisher emits to Kafka only after the database commit. It retries safely; duplicate events are expected and consumers deduplicate by event ID.
4. A partitioned order workflow/Saga consumes the request and calls domain commands. Inventory atomically reserves stock with a durable reservation ID; payment uses the order ID as its idempotency key. Persist each Saga step and compensate on failure.
5. Order Service alone transitions the order state. Inventory, Payment and Delivery own their own states. Events carry updates; read models are projections, never alternative masters.
6. Notification and analytics run asynchronously and never block order acceptance.

## Capacity planning checklist

10,000 TPS is a measured system property, not a configuration toggle. Define whether TPS means accepted HTTP requests, accepted orders, or fully completed orders. They are different rates.

- Use a multi-node Kubernetes deployment; run multiple stateless gateway and service replicas behind load balancers.
- Redis-backed per-client limits protect fairness; aggregate admission control must protect total platform capacity. Size Redis and avoid one hot key. Rate-limit rejection should be a clear `429` response.
- Kafka: multi-broker cluster, replication factor 3, partitions sized to required parallelism, producer batching/compression, consumer concurrency and lag alerts. Partition by order ID when per-order ordering matters.
- H2 for local development (PostgreSQL or another production database for deployment): independent domain databases, connection pools sized in aggregate against server connection capacity, short transactions, proper indexes, durable idempotency constraints, outbox indexes, backups and tested recovery. Do not set a huge Hikari pool on every replica; `replicas × pool size` must fit database capacity.
- Inventory contention is a likely bottleneck for popular SKUs. Use conditional atomic updates/reservation rows and partition inventory by product/warehouse where needed; do not rely on a single globally hot row for every order.
- Cache catalog reads only as a rebuildable projection with a clear freshness policy. Catalog's database remains authoritative. Never cache available stock as the authority for reservations.
- Set timeouts, bounded retries with jitter, circuit breakers, backpressure, dead-letter topics, replay tooling and reconciliation jobs. Do not retry non-idempotent side effects blindly.
- Observability: request rate, p50/p95/p99 latency, 4xx/5xx, DB pool wait, lock waits, Kafka publish errors/consumer lag, Redis latency, order state age and Saga compensation rate.

## Benchmark protocol

1. Specify workload (payload sizes, read/write ratio, SKU skew, duplicate retries), data volume, target p95/p99 latency and acceptable error rate.
2. Start at 100 TPS, then 500, 1k, 2k, 5k, 8k and 10k TPS; hold each step long enough to stabilize.
3. Use a separate load generator and enough unique authenticated API keys so the per-key limiter does not artificially cap aggregate load. Keep the gateway enabled.
4. Test both acceptance throughput and end-to-end completion throughput. Monitor Kafka lag and pending-order age so buffering does not disguise a downstream bottleneck.
5. Run soak, burst, dependency failure, duplicate request, broker restart and database failover tests.
6. Record hardware, replicas, pool sizes, Kafka partitions, p95/p99 latency, error rate and sustained throughput. Only claim 10k TPS after repeatable runs meet the SLO without unbounded lag.

## Known limitations in this repository

- The current order API still performs synchronous downstream calls; the full outbox/Saga flow described above is an architectural next step, not already implemented.
- Payment and delivery demo state is in-memory and disappears on restart.
- Local Kafka is single-broker with replication factor 1; it is not highly available.
- The local Compose environment and load script have not been benchmarked at 10k TPS.


## H2 profile notes

The local Compose stack uses one file-based H2 database and named volume per data-owning service. H2 is suitable for demos and functional tests, not as a credible basis for claiming 10,000 TPS in production. For a production throughput target, retain single-writer ownership per domain but use a production database with replication, backups, metrics and load-tested connection pools. Never point multiple service replicas at the same H2 file unless using a specifically supported shared-server topology; this project intentionally gives each domain one file volume for local development.
