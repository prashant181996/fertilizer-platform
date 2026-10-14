# Kubernetes horizontal scaling template

These manifests are a **production-oriented starting point**, not a turnkey managed-infrastructure installation and not
proof of 10,000 TPS. They assume the app images have been built and pushed to a registry and that PostgreSQL, Kafka, and
Redis are provisioned separately with production-grade durability and network/security settings.

## What the template configures

- API Gateway starts with 3 replicas and has a CPU HPA from 3 to 20.
- Product Service starts with 3 replicas and has a CPU HPA from 3 to 20.
- Order Service starts with 4 replicas and KEDA scales it from 4 to 8 using CPU and consumer lag. It uses listener
  concurrency 6, so 8 replicas can provide up to 48 active consumers per listener group.
- Inventory and Payment each use concurrency 12 and KEDA scale from 3 to 4 replicas, allowing up to 48 active consumers
  per group.
- Delivery and Notification use concurrency 6 and KEDA scale from 2 to 8 replicas.
- Kafka topics are configured for 48 partitions, keyed by `orderId`. A consumer group cannot actively consume a given
  partition with more than one member at once.
- Database pool limits are set conservatively in the template. Recalculate connection budgets for all replicas and all
  services before production.

## Prerequisites

1. A Kubernetes cluster with a working ingress/load-balancer implementation, metrics-server, and KEDA installed.
2. Seven container images built from the project Dockerfiles and pushed to your registry.
3. Managed or properly operated PostgreSQL databases named for each service, a multi-broker Kafka cluster, and a highly
   available Redis endpoint.
4. A Kafka endpoint reachable as `kafka-bootstrap:9092` from the `fertilizer` namespace, and Redis reachable
   as `redis-primary:6379`. Change these names in the ConfigMap if your endpoints differ.
5. Database DNS names used by the template (`postgres-orders`, `postgres-products`, `postgres-inventory`
   , `postgres-payments`, `postgres-delivery`) must resolve from the namespace. Change the JDBC URLs if needed.
6. Production secrets from a secret manager. The included Secret contains obvious placeholders and must not be used
   as-is.

## Build and push images

Build from the project root, changing `YOUR_REGISTRY` to your container registry:

```bash
docker build -f api-gateway/Dockerfile -t YOUR_REGISTRY/fertilizer-api-gateway:1.0.0 .
docker build -f order-service/Dockerfile -t YOUR_REGISTRY/fertilizer-order-service:1.0.0 .
docker build -f product-service/Dockerfile -t YOUR_REGISTRY/fertilizer-product-service:1.0.0 .
docker build -f inventory-service/Dockerfile -t YOUR_REGISTRY/fertilizer-inventory-service:1.0.0 .
docker build -f payment-service/Dockerfile -t YOUR_REGISTRY/fertilizer-payment-service:1.0.0 .
docker build -f delivery-service/Dockerfile -t YOUR_REGISTRY/fertilizer-delivery-service:1.0.0 .
docker build -f notification-service/Dockerfile -t YOUR_REGISTRY/fertilizer-notification-service:1.0.0 .
```

Push all seven tags, then replace the image values in `production-scale.yaml`.

## Apply

Install KEDA and metrics-server according to their official instructions first. Then edit endpoints, secret values, and
images. From this directory's parent project root:

```bash
kubectl apply -f k8s/production-scale.yaml
kubectl apply -f k8s/autoscaling.yaml
kubectl -n fertilizer get deployments,pods,services,hpa,scaledobjects
```

The `ScaledObject` resources require the KEDA CRDs. For a secured Kafka cluster, add a KEDA `TriggerAuthentication` and
appropriate SASL/TLS settings rather than using an unauthenticated broker connection.

## Kafka partition and concurrency rule

For a topic with 48 partitions, at most 48 consumers in one consumer group can actively process its partitions. Initial
concurrency targets are intentionally aligned:

- Order Service: 8 replicas × 6 listener threads = up to 48 consumers per Order listener group.
- Inventory Service: 4 replicas × 12 listener threads = up to 48 consumers per group.
- Payment Service: 4 replicas × 12 listener threads = up to 48 consumers per group.
- Delivery/Notification: 8 replicas × 6 listener threads = up to 48 consumers per group.

This is a starting topology, not a throughput prediction. Partition throughput depends on message size, broker I/O,
replication, consumer processing, database locks, hot product keys, and network. Some topics have less traffic than
others. Tune per-topic partitions and per-service concurrency using consumer lag and measured end-to-end results.

The topic replication factor is set to 3 in the Kubernetes ConfigMap; only use it when the Kafka cluster has at least
three brokers. The local Docker Compose broker uses replication factor 1. Increasing partitions on existing topics may
change key-to-partition mapping; perform a planned migration if strict per-key ordering matters.

## Verify the target

1. Run staged load at 100, 500, 1,000, 2,500, 5,000 and 10,000 offered requests/sec from separate load generators.
2. Measure Gateway ingress, durable `202` acceptance, and completed orders separately.
3. Ensure consumer lag and outbox age remain stable at steady state, and verify correct stock/payment state under
   retries and duplicate events.
4. Watch database connection pools, query/lock latency, Kafka broker/network/disk saturation, p95/p99 latency, errors,
   CPU, memory, and GC.
5. Run a soak test and a failure/recovery test. Do not claim 10k TPS until these tests pass on the intended production
   infrastructure.

The included single-broker Compose stack is only for local functional tests and initial profiling. Do not use it to
claim production throughput or high availability.
