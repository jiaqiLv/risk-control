package com.risk.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient configuration for making HTTP requests to downstream services.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean
    public WebClient orchestratorWebClient(WebClient.Builder builder, GatewayProperties properties) {
        return builder
                .baseUrl(properties.getOrchestratorBaseUrl())
                .build();
    }
}
