package com.risk.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway service configuration properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /**
     * Base URL of the orchestrator service
     */
    private String orchestratorBaseUrl = "http://localhost:8081";

    /**
     * Request timeout in milliseconds
     */
    private int timeoutMs = 5000;

    /**
     * Enable request logging
     */
    private boolean logRequests = true;

    /**
     * Enable response logging
     */
    private boolean logResponses = true;
}
