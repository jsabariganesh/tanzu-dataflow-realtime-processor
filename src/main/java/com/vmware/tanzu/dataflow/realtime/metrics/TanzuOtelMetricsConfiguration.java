package com.vmware.tanzu.dataflow.realtime.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableConfigurationProperties(TanzuMetricsProperties.class)
public class TanzuOtelMetricsConfiguration implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TanzuOtelMetricsConfiguration.class);

    private final List<SdkMeterProvider> activeMeterProviders = Collections.synchronizedList(new ArrayList<>());

    @Bean
    public CfEnvironmentDetector cfEnvironmentDetector() {
        CfEnvironmentDetector detector = new CfEnvironmentDetector();
        log.info("Initialized Cloud Foundry environment detector. Detected resource attributes: {}",
                detector.getResourceAttributes());
        if (detector.hasInstanceIdentity()) {
            log.info("CF Instance Identity certificates detected: cert={}, key={}",
                    detector.getCertPath(), detector.getKeyPath());
        } else {
            log.info("CF Instance Identity certificates not detected or files unreadable; mTLS client auth disabled");
        }
        return detector;
    }

    @Bean(name = "tanzuJvmOtelMeterRegistry")
    @ConditionalOnProperty(prefix = "tanzu.metrics.jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeterRegistry tanzuJvmOtelMeterRegistry(TanzuMetricsProperties properties,
                                                    CfEnvironmentDetector cfDetector,
                                                    Clock clock) {
        String endpoint = properties.getJvm().getEndpoint();
        log.info("Initializing Tanzu JVM OTel MeterRegistry targeting endpoint: {}", endpoint);
        return createRegistry(
                "jvm-otel",
                endpoint,
                TanzuMetricsFilter.createJvmFilter(),
                properties,
                cfDetector,
                clock
        );
    }

    @Bean(name = "tanzuCustomOtelMeterRegistry")
    @ConditionalOnProperty(prefix = "tanzu.metrics.custom", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeterRegistry tanzuCustomOtelMeterRegistry(TanzuMetricsProperties properties,
                                                       CfEnvironmentDetector cfDetector,
                                                       Clock clock) {
        String endpoint = properties.getCustom().getEndpoint();
        log.info("Initializing Tanzu Custom OTel MeterRegistry targeting endpoint: {}", endpoint);
        return createRegistry(
                "custom-otel",
                endpoint,
                TanzuMetricsFilter.createCustomFilter(),
                properties,
                cfDetector,
                clock
        );
    }

    private MeterRegistry createRegistry(String registryName,
                                         String endpoint,
                                         MeterFilter filter,
                                         TanzuMetricsProperties properties,
                                         CfEnvironmentDetector cfDetector,
                                         Clock clock) {

        OtlpHttpMetricExporter exporter = buildExporter(registryName, endpoint, properties, cfDetector);

        PeriodicMetricReader reader = PeriodicMetricReader.builder(exporter)
                .setInterval(properties.getStep())
                .build();

        ResourceBuilder resourceBuilder = Resource.builder();
        cfDetector.getResourceAttributes().forEach(resourceBuilder::put);
        if (!cfDetector.getResourceAttributes().containsKey("service.name")
                && cfDetector.getResourceAttributes().containsKey("app_name")) {
            resourceBuilder.put("service.name", cfDetector.getResourceAttributes().get("app_name"));
        }
        Resource resource = Resource.getDefault().merge(resourceBuilder.build());

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(reader)
                .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();

        MeterRegistry meterRegistry = OpenTelemetryMeterRegistry.builder(openTelemetry)
                .setClock(clock)
                .build();

        meterRegistry.config().meterFilter(filter);
        activeMeterProviders.add(meterProvider);

        return meterRegistry;
    }

    private OtlpHttpMetricExporter buildExporter(String name,
                                                  String endpoint,
                                                  TanzuMetricsProperties properties,
                                                  CfEnvironmentDetector cfDetector) {
        OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(10))
                .setConnectTimeout(Duration.ofSeconds(10));

        if (cfDetector.hasInstanceIdentity()) {
            try {
                byte[] certBytes = cfDetector.getCertBytes();
                byte[] keyBytes = cfDetector.getKeyBytes();
                if (certBytes != null && keyBytes != null) {
                    builder.setClientTls(keyBytes, certBytes);
                    log.info("[{}] Configured mTLS client identity for OTel Collector at {}", name, endpoint);
                }
            } catch (Exception e) {
                log.warn("[{}] Failed to configure mTLS client identity: {}", name, e.getMessage());
            }
        }

        if (properties.getCaPath() != null && !properties.getCaPath().isBlank()) {
            try {
                File caFile = new File(properties.getCaPath());
                if (caFile.exists() && caFile.canRead()) {
                    builder.setTrustedCertificates(Files.readAllBytes(caFile.toPath()));
                    log.info("[{}] Configured custom trusted CA certificate from {}", name, caFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("[{}] Failed to configure custom CA certificate from {}: {}",
                        name, properties.getCaPath(), e.getMessage());
            }
        }

        return builder.build();
    }

    @Override
    public void destroy() {
        for (SdkMeterProvider provider : activeMeterProviders) {
            try {
                log.info("Closing SdkMeterProvider and flushing pending OTel metrics");
                provider.close();
            } catch (Exception e) {
                log.warn("Error closing SdkMeterProvider: {}", e.getMessage());
            }
        }
        activeMeterProviders.clear();
    }
}
