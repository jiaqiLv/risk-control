package com.riskcontrol.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("risk-control", "1.0.0");
    }

    @Bean
    public TextMapPropagator textMapPropagator() {
        return TextMapPropagator.composite(
                JaegerPropagator.getInstance()
        );
    }
}
