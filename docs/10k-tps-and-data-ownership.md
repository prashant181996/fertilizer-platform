# 10k TPS capacity planning and data ownership

## What TPS means

Report these separately:

1. **Ingress TPS:** HTTP requests accepted by the Gateway.
2. **Order-acceptance TPS:** orders durably committed with an outbox row and returned as `202`.
3. **Completion TPS:** orders that reach a terminal business state (`CONFIRMED` or `FAILED`).

The platform is not proven to sustain 10,000 TPS until all three are measured under representative traffic and the
backlog is stable. A queue can hide overload temporarily; a growing Kafka lag/outbox backlog means the system is not
keeping up.

## Request path and critical path

The synchronous request path performs a cached product lookup and one local PostgreSQL transaction for the order plus
outbox event. Inventory, payment simulation, order confirmation, delivery, and notification are asynchronous Kafka
consumers. The order endpoint does not wait for payment or delivery and responds with `202 Accepted` only after
persistence.

## Data ownership

| Service | Authoritative data | Consistency boundary |
|---|---|---|
| Product | Catalog and prices | PostgreSQL product DB; short-lived Redis cache |
| Order | Order state, idempotency key, outbox, Saga state | One PostgreSQL transaction for order + initial event; cross-replica outbox claims use `SKIP LOCKED` plus a lease |
| Inventory | Available stock and reservations | Conditional atomic update + unique reservation per order |
| Payment | Payment authorization record | Unique payment record per order; demo authorization only |
| Delivery | Shipment state | Unique shipment per order, created from confirmed event |
| Notification | Delivery attempt/logs in this demo | Consumer of confirmed-order event; provider not integrated |

No service should update another service's tables. Use events or APIs. Do not use Redis as the source of truth for stock
or payments.

## Initial capacity estimate

Do not select replica counts from TPS alone. Benchmark each component independently and end-to-end. For an observed
sustainable per-instance throughput `r`, a rough first estimate is `ceil(targetTPS / r)` instances before headroom; add
capacity for bursts, failure of one zone, uneven partitions, deployments and retries. This estimate is only useful
when `r` is measured with realistic dependencies and latency targets.

The development Compose stack now creates 48-partition Kafka topics and exposes configurable listener concurrency, but
it still has one PostgreSQL instance, one Kafka broker, and one Redis instance. It is intended for functional testing
and initial profiling, not production 10k TPS. Production needs multi-node/managed infrastructure, replication,
authentication, encryption, backups, resource sizing, tracing, SLO alerts, failover tests and a sustained load test.

## Load-test acceptance criteria

- Ramp through 100, 500, 1,000, 2,500, 5,000 and 10,000 offered requests/sec.
- Define a latency objective (for example p95 and p99) before running the test.
- Confirm the accepted request rate, status-code/error rate and durable order count.
- Confirm the eventual terminal-state count and expected stock/payment outcomes.
- Watch Kafka consumer lag, outbox age, dead-letter topic count, DB connection pool saturation, locks, CPU, memory and
  GC.
- Run a sustained soak test and a failure/recovery test, not only a short spike.
- Make sure k6 has enough CPU/network and uses realistic client-key distribution. The Gateway's rate limit is per key.

## Required production work

- Replace demo payment authorization with a real provider, provider idempotency, reconciliation and secure secret
  handling.
- Use a database migration tool instead of Hibernate `ddl-auto=update`.
- Deploy Kafka with 48 or benchmark-derived partitions, replication factor 3 and suitable minimum ISR; the local
  single-broker config must use replication factor 1. See `k8s/production-scale.yaml` and `k8s/autoscaling.yaml` for
  starting deployment and KEDA scaling templates.
- Deploy PostgreSQL and Redis in highly available configurations and budget connection pools across all replicas.
- Add OAuth2/JWT authentication, authorization, request quotas, schema validation, rate-limit policies and audit
  controls.
- Implement alerting and controlled replay for `.DLT` topics, plus recovery playbooks.
- Test duplicates, crash windows, provider timeouts, hot-product inventory contention, partition imbalance, node failure
  and long-duration throughput.
