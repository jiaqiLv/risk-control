package com.risk.gateway.config;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.gateway.model.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for interacting with Nacos configuration center.
 * Handles reading and writing Sentinel flow control rules to/from Nacos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosConfigService {

    private final NacosProperties nacosProperties;
    private final ObjectMapper objectMapper;
    private ConfigService configService;

    /**
     * Initialize Nacos ConfigService.
     */
    @PostConstruct
    public void init() {
        try {
            configService = ConfigFactory.createConfigService(nacosProperties.getServerAddr());
            log.info("Nacos ConfigService initialized successfully with server: {}",
                    nacosProperties.getServerAddr());
        } catch (NacosException e) {
            log.error("Failed to initialize Nacos ConfigService", e);
            throw new RuntimeException("Failed to connect to Nacos", e);
        }
    }

    /**
     * Publish rate limit rules to Nacos.
     *
     * @param rules List of rate limit rules to publish
     * @return true if successful, false otherwise
     */
    public boolean publishRules(List<RateLimitRule> rules) {
        try {
            // Convert RateLimitRule list to JSON for Nacos
            String rulesJson = objectMapper.writeValueAsString(rules);

            boolean success = configService.publishConfig(
                    nacosProperties.getFlowRulesDataId(),
                    nacosProperties.getGroupId(),
                    rulesJson
            );

            if (success) {
                log.info("Successfully published {} rules to Nacos (dataId: {}, groupId: {})",
                        rules.size(), nacosProperties.getFlowRulesDataId(), nacosProperties.getGroupId());
            } else {
                log.error("Failed to publish rules to Nacos");
            }

            return success;
        } catch (Exception e) {
            log.error("Error publishing rules to Nacos", e);
            return false;
        }
    }

    /**
     * Get rate limit rules from Nacos.
     *
     * @return List of rate limit rules, or empty list if not found
     */
    public List<RateLimitRule> getRules() {
        try {
            String config = configService.getConfig(
                    nacosProperties.getFlowRulesDataId(),
                    nacosProperties.getGroupId(),
                    5000  // timeout in ms
            );

            if (config == null || config.isEmpty()) {
                log.warn("No rules found in Nacos (dataId: {}, groupId: {})",
                        nacosProperties.getFlowRulesDataId(), nacosProperties.getGroupId());
                return new ArrayList<>();
            }

            // Parse JSON to RateLimitRule list
            return objectMapper.readValue(
                    config,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RateLimitRule.class)
            );
        } catch (Exception e) {
            log.error("Error reading rules from Nacos", e);
            return new ArrayList<>();
        }
    }

    /**
     * Create Nacos data source for Sentinel.
     * This allows Sentinel to automatically load rules from Nacos.
     *
     * @return ReadableDataSource for Sentinel flow rules
     */
    public ReadableDataSource<String, List<com.alibaba.csp.sentinel.slots.block.flow.FlowRule>> createFlowDataSource() {
        try {
            NacosDataSource<List<com.alibaba.csp.sentinel.slots.block.flow.FlowRule>> dataSource =
                    new NacosDataSource<>(
                            nacosProperties.getServerAddr(),
                            nacosProperties.getGroupId(),
                            nacosProperties.getFlowRulesDataId(),
                            source -> JSON.parseObject(source, new TypeReference<List<com.alibaba.csp.sentinel.slots.block.flow.FlowRule>>() {})
                    );

            log.info("Created Nacos data source for Sentinel flow rules");
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create Nacos data source for Sentinel", e);
            throw new RuntimeException("Failed to create Nacos data source", e);
        }
    }

    /**
     * Check if Nacos connection is healthy.
     *
     * @return true if connected, false otherwise
     */
    public boolean isHealthy() {
        try {
            return configService != null && configService.getServerStatus().equals("UP");
        } catch (Exception e) {
            log.error("Error checking Nacos health status", e);
            return false;
        }
    }
}
