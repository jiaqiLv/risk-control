package com.risk.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway Service Application
 *
 * Main entry point for the Risk Control Gateway Service.
 * Acts as the unified API gateway for all risk assessment requests.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
