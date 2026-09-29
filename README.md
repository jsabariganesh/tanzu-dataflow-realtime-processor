# DataFlow Realtime Processor

A cloud-native Spring Boot real-time streaming processor designed for Tanzu Platform and VMware Tanzu Application Service (TAS).

## Key Features
- **Realtime Stream Processing**: Consumes order transactions from RabbitMQ exchange and persists enriched records to MySQL.
- **Embedded Simulation Engine**: Dynamically generates realistic high-throughput order streams with configurable generation intervals and runtime pause/resume capability.
- **Comprehensive Observability & Tanzu OTel Metrics**:
  - Exposes standard `/actuator/prometheus` for metric scrapers and Prometheus integrations.
  - Native Tanzu Dual OpenTelemetry integration (`TANZU_METRICS_JVM_ENDPOINT` on 9565 and `TANZU_METRICS_CUSTOM_ENDPOINT` on 9566) with mutual TLS (`CF_INSTANCE_CERT` / `CF_INSTANCE_KEY`) and automatic Cloud Foundry container resource attribution (`app_id`, `org_name`, `space_name`, `stream_name`, `stream_app_name`).
- **Standard Platform Logging**: Emits clean SLF4J / Logback application logs directly to stdout/stderr for standard Cloud Foundry log aggregation. No direct log forwarding or syslog drains required.

## Custom Emitted Stream Metrics
- `dataflow.orders.amount.total`: Cumulative dollar amount processed across all orders.
- `dataflow.rabbitmq.messages.produced`: Total messages published to RabbitMQ.
- `dataflow.rabbitmq.messages.consumed`: Total messages consumed and processed.
- `dataflow.stream.end_to_end.latency`: Latency timer (p50, p95, p99) measuring message creation to database persistence.
- `dataflow.mysql.insert.duration`: Database insert operation latency timer.
- `dataflow.mysql.total_orders_count`: Real-time gauge of total order records in MySQL.
- `dataflow.stream.errors`: Error counter tracking stream failure events.

## Endpoints
- `GET /api/stats`: Real-time processing statistics.
- `GET /api/orders/recent`: Most recent 20 order transactions.
- `POST /api/orders`: Ingest manual order event.
- `POST /api/simulation/toggle`: Toggle background stream generator.
- `GET /actuator/prometheus`: Native Prometheus scraping endpoint.
- `GET /actuator/health`: Application health status.

## Build & Deploy
```bash
# Build
./mvnw clean package

# Deploy to Cloud Foundry
cf push
```
