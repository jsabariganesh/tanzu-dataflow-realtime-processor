package com.vmware.tanzu.dataflow.realtime.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

import java.util.List;

/**
 * Filters metrics between JVM/system metrics and application custom metrics.
 */
public final class TanzuMetricsFilter {

    private static final List<String> JVM_PREFIXES = List.of(
            "jvm.",
            "process.",
            "system.",
            "disk."
    );

    private TanzuMetricsFilter() {}

    public static boolean isJvmOrSystemMetric(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            return false;
        }
        for (String prefix : JVM_PREFIXES) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static MeterFilter createJvmFilter() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return isJvmOrSystemMetric(id.getName()) ? MeterFilterReply.ACCEPT : MeterFilterReply.DENY;
            }
        };
    }

    public static MeterFilter createCustomFilter() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return isJvmOrSystemMetric(id.getName()) ? MeterFilterReply.DENY : MeterFilterReply.ACCEPT;
            }
        };
    }
}
