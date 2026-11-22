package com.risk.gateway;

import com.risk.gateway.config.NacosProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Gateway Service Application
 *
 * Main entry point for the Risk Control Gateway Service.
 * Acts as the unified API gateway for all risk assessment requests.
 */
@SpringBootApplication
@EnableConfigurationProperties(NacosProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
