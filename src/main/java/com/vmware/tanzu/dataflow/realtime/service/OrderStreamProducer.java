package com.vmware.tanzu.dataflow.realtime.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OrderStreamProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderStreamProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final String routingKey;
    private final Counter producedCounter;

    public OrderStreamProducer(
            RabbitTemplate rabbitTemplate,
            MeterRegistry meterRegistry,
            @Value("${dataflow.rabbitmq.exchange:dataflow.orders.exchange}") String exchangeName,
            @Value("${dataflow.rabbitmq.routingkey:dataflow.orders.key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.producedCounter = Counter.builder("dataflow.rabbitmq.messages.produced")
                .description("Total messages published to RabbitMQ exchange")
                .tag("exchange", exchangeName)
                .register(meterRegistry);
    }

    public void publishOrder(Map<String, Object> orderPayload) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, orderPayload);
        producedCounter.increment();
        logger.debug("Published order event to RabbitMQ: {}", orderPayload.get("orderId"));
    }

    public long getProducedCount() {
        return (long) producedCounter.count();
    }
}
