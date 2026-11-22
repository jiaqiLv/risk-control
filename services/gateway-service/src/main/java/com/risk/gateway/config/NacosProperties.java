package com.risk.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Nacos integration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "nacos.config")
public class NacosProperties {

    /**
     * Nacos server address (e.g., localhost:8848).
     */
    private String serverAddr;

    /**
     * Nacos namespace for isolation.
     */
    private String namespace;

    /**
     * Nacos group ID.
     */
    private String groupId;

    /**
     * Data ID for storing flow control rules.
     */
    private String flowRulesDataId;
}
