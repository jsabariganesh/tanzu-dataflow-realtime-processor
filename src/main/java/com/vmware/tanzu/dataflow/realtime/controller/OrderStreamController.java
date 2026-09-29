package com.vmware.tanzu.dataflow.realtime.controller;

import com.vmware.tanzu.dataflow.realtime.model.OrderTransaction;
import com.vmware.tanzu.dataflow.realtime.repository.OrderTransactionRepository;
import com.vmware.tanzu.dataflow.realtime.service.OrderStreamConsumer;
import com.vmware.tanzu.dataflow.realtime.service.OrderStreamProducer;
import com.vmware.tanzu.dataflow.realtime.service.RealtimeSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class OrderStreamController {

    private final OrderStreamProducer producer;
    private final OrderStreamConsumer consumer;
    private final RealtimeSimulator simulator;
    private final OrderTransactionRepository repository;

    public OrderStreamController(
            OrderStreamProducer producer,
            OrderStreamConsumer consumer,
            RealtimeSimulator simulator,
            OrderTransactionRepository repository) {
        this.producer = producer;
        this.consumer = consumer;
        this.simulator = simulator;
        this.repository = repository;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("status", "UP");
        stats.put("simulationEnabled", simulator.isEnabled());
        stats.put("messagesProduced", producer.getProducedCount());
        stats.put("messagesConsumed", consumer.getConsumedCount());
        stats.put("mysqlRecordsCount", repository.count());
        stats.put("streamErrors", consumer.getErrorCount());
        stats.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/orders/recent")
    public ResponseEntity<List<OrderTransaction>> getRecentOrders() {
        return ResponseEntity.ok(repository.findTop20ByOrderByProcessedAtDesc());
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> submitOrder(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            payload = new HashMap<>();
        }
        String orderId = (String) payload.computeIfAbsent("orderId", k -> "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payload.putIfAbsent("customerId", "CUST-MANUAL");
        payload.putIfAbsent("amount", 99.99);
        payload.putIfAbsent("currency", "USD");
        payload.putIfAbsent("status", "COMPLETED");
        payload.putIfAbsent("createdTimestamp", System.currentTimeMillis());

        producer.publishOrder(payload);
        return ResponseEntity.ok(Map.of("message", "Order queued to RabbitMQ", "orderId", orderId));
    }

    @PostMapping("/simulation/toggle")
    public ResponseEntity<Map<String, Object>> toggleSimulation() {
        boolean newState = simulator.toggle();
        return ResponseEntity.ok(Map.of("simulationEnabled", newState));
    }
}
