package com.vmware.tanzu.dataflow.realtime.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.vmware.tanzu.dataflow.realtime.model.OrderTransaction;
import com.vmware.tanzu.dataflow.realtime.repository.OrderTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderStreamConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderStreamConsumer.class);

    private final OrderTransactionRepository repository;
    private final Counter consumedCounter;
    private final Counter errorCounter;
    private final Counter amountCounter;
    private final Timer endToEndLatencyTimer;
    private final Timer dbInsertTimer;
    private final AtomicLong dbRowCountGauge;

    public OrderStreamConsumer(OrderTransactionRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.consumedCounter = Counter.builder("dataflow.rabbitmq.messages.consumed")
                .description("Total messages consumed from RabbitMQ queue")
                .tag("queue", "dataflow.orders.queue")
                .tag("status", "SUCCESS")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("dataflow.stream.errors")
                .description("Total processing errors in stream")
                .tag("type", "consumer_error")
                .register(meterRegistry);

        this.amountCounter = Counter.builder("dataflow.orders.amount.total")
                .description("Cumulative order amount processed in USD")
                .tag("currency", "USD")
                .register(meterRegistry);

        this.endToEndLatencyTimer = Timer.builder("dataflow.stream.end_to_end.latency")
                .description("End-to-end stream latency from message creation to persistence")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.dbInsertTimer = Timer.builder("dataflow.mysql.insert.duration")
                .description("Time taken to insert order record into database")
                .register(meterRegistry);

        this.dbRowCountGauge = new AtomicLong(0);
        meterRegistry.gauge("dataflow.mysql.total_orders_count", dbRowCountGauge);
    }

    @RabbitListener(queues = "${dataflow.rabbitmq.queue:dataflow.orders.queue}")
    public void receiveOrder(Map<String, Object> orderPayload) {
        long receiveTime = System.currentTimeMillis();
        try {
            String orderId = (String) orderPayload.get("orderId");
            String customerId = (String) orderPayload.get("customerId");
            Double amount = ((Number) orderPayload.getOrDefault("amount", 0.0)).doubleValue();
            String currency = (String) orderPayload.getOrDefault("currency", "USD");
            String status = (String) orderPayload.getOrDefault("status", "PROCESSED");
            Long createdTimestamp = ((Number) orderPayload.getOrDefault("createdTimestamp", receiveTime)).longValue();

            long latencyMs = Math.max(0, receiveTime - createdTimestamp);
            endToEndLatencyTimer.record(java.time.Duration.ofMillis(latencyMs));

            LocalDateTime createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdTimestamp), ZoneId.systemDefault());
            LocalDateTime processedAt = LocalDateTime.now();

            OrderTransaction transaction = new OrderTransaction(
                    orderId, customerId, amount, currency, status, latencyMs, createdAt, processedAt
            );

            dbInsertTimer.record(() -> repository.save(transaction));

            consumedCounter.increment();
            amountCounter.increment(amount);
            dbRowCountGauge.set(repository.count());

            logger.info("Processed order [{}] for customer [{}] with amount [{} {}] in {}ms (Total in MySQL: {})",
                    orderId, customerId, amount, currency, latencyMs, dbRowCountGauge.get());

        } catch (Exception e) {
            errorCounter.increment();
            logger.error("Failed to process message from RabbitMQ: {}", e.getMessage(), e);
        }
    }

    public long getConsumedCount() {
        return (long) consumedCounter.count();
    }

    public long getErrorCount() {
        return (long) errorCounter.count();
    }

    public long getDbCount() {
        return dbRowCountGauge.get();
    }
}
