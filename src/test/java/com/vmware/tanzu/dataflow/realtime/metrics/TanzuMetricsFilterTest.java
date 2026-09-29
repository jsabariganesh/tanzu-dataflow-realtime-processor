package com.vmware.tanzu.dataflow.realtime.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TanzuMetricsFilterTest {

    @Test
    void testJvmPrefixDetection() {
        assertTrue(TanzuMetricsFilter.isJvmOrSystemMetric("jvm.memory.used"));
        assertTrue(TanzuMetricsFilter.isJvmOrSystemMetric("process.cpu.usage"));
        assertTrue(TanzuMetricsFilter.isJvmOrSystemMetric("system.load.average.1m"));
        assertTrue(TanzuMetricsFilter.isJvmOrSystemMetric("disk.free"));
    }

    @Test
    void testCustomMetricDetection() {
        assertFalse(TanzuMetricsFilter.isJvmOrSystemMetric("dataflow.orders.amount.total"));
        assertFalse(TanzuMetricsFilter.isJvmOrSystemMetric("dataflow.rabbitmq.messages.produced"));
        assertFalse(TanzuMetricsFilter.isJvmOrSystemMetric("dataflow.stream.end_to_end.latency"));
        assertFalse(TanzuMetricsFilter.isJvmOrSystemMetric("dataflow.mysql.total_orders_count"));
    }
}
