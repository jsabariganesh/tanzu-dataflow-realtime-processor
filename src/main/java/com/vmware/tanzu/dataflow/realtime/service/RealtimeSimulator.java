package com.vmware.tanzu.dataflow.realtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RealtimeSimulator {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeSimulator.class);

    private final OrderStreamProducer producer;
    private final AtomicBoolean enabled;
    private final Random random = new Random();

    public RealtimeSimulator(
            OrderStreamProducer producer,
            @Value("${dataflow.simulation.enabled:true}") boolean enabled) {
        this.producer = producer;
        this.enabled = new AtomicBoolean(enabled);
    }

    @Scheduled(fixedRateString = "${dataflow.simulation.rate-ms:2000}")
    public void generateStreamOrder() {
        if (!enabled.get()) {
            return;
        }

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String customerId = "CUST-" + (1000 + random.nextInt(9000));
        double amount = Math.round((10.0 + (random.nextDouble() * 490.0)) * 100.0) / 100.0;

        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "customerId", customerId,
                "amount", amount,
                "currency", "USD",
                "status", "COMPLETED",
                "createdTimestamp", System.currentTimeMillis()
        );

        producer.publishOrder(order);
    }

    public boolean toggle() {
        boolean newState = !enabled.get();
        enabled.set(newState);
        logger.info("Realtime order stream simulation enabled: {}", newState);
        return newState;
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}
