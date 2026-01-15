package com.risk.gateway.service;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.gateway.model.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Sentinel rate limit rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ObjectMapper objectMapper;
    private static final String RULES_FILE_PATH = "src/main/resources/sentinel-rules.json";

    /**
     * Get all rate limit rules.
     *
     * @return List of rate limit rules
     */
    public List<RateLimitRule> getAllRules() {
        List<FlowRule> flowRules = FlowRuleManager.getRules();
        return flowRules.stream()
                .map(this::convertToRateLimitRule)
                .collect(Collectors.toList());
    }

    /**
     * Get rate limit rule by resource.
     *
     * @param resource Resource name (API endpoint path)
     * @return Rate limit rule or null if not found
     */
    public RateLimitRule getRuleByResource(String resource) {
        List<FlowRule> flowRules = FlowRuleManager.getRules();
        return flowRules.stream()
                .filter(rule -> rule.getResource().equals(resource))
                .findFirst()
                .map(this::convertToRateLimitRule)
                .orElse(null);
    }

    /**
     * Add or update a rate limit rule.
     *
     * @param rule Rate limit rule to add
     * @return Updated list of all rules
     */
    public List<RateLimitRule> addOrUpdateRule(RateLimitRule rule) {
        List<FlowRule> flowRules = new ArrayList<>(FlowRuleManager.getRules());

        // Check if rule for resource already exists
        boolean ruleExists = flowRules.stream()
                .anyMatch(r -> r.getResource().equals(rule.getResource()));

        FlowRule flowRule = convertToFlowRule(rule);

        if (ruleExists) {
            // Update existing rule
            log.info("Updating rate limit rule for resource: {}", rule.getResource());
            flowRules.removeIf(r -> r.getResource().equals(rule.getResource()));
        } else {
            // Add new rule
            log.info("Adding new rate limit rule for resource: {}", rule.getResource());
        }

        flowRules.add(flowRule);

        // Load rules into Sentinel
        FlowRuleManager.loadRules(flowRules);

        // Persist to file
        persistRulesToFile(flowRules);

        return getAllRules();
    }

    /**
     * Delete a rate limit rule by resource.
     *
     * @param resource Resource name (API endpoint path)
     * @return Updated list of all rules
     */
    public List<RateLimitRule> deleteRule(String resource) {
        List<FlowRule> flowRules = new ArrayList<>(FlowRuleManager.getRules());

        boolean removed = flowRules.removeIf(rule -> rule.getResource().equals(resource));

        if (removed) {
            log.info("Deleted rate limit rule for resource: {}", resource);
            FlowRuleManager.loadRules(flowRules);
            persistRulesToFile(flowRules);
        } else {
            log.warn("No rule found for resource: {}", resource);
        }

        return getAllRules();
    }

    /**
     * Convert RateLimitRule to FlowRule.
     */
    private FlowRule convertToFlowRule(RateLimitRule rateLimitRule) {
        FlowRule flowRule = new FlowRule();
        flowRule.setResource(rateLimitRule.getResource());
        flowRule.setLimitApp(rateLimitRule.getLimitApp());
        flowRule.setGrade(rateLimitRule.getGrade());
        flowRule.setCount(rateLimitRule.getCount().doubleValue());
        flowRule.setStrategy(rateLimitRule.getStrategy());
        flowRule.setControlBehavior(rateLimitRule.getControlBehavior());
        flowRule.setClusterMode(rateLimitRule.getClusterMode());

        // Set warmup period if control behavior is warmup (not strategy)
        // Fixed: STRATEGY_WARM_UP doesn't exist, check control behavior instead
        if (rateLimitRule.getControlBehavior().equals(RuleConstant.CONTROL_BEHAVIOR_WARM_UP) ||
            rateLimitRule.getControlBehavior().equals(RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER)) {
            flowRule.setWarmUpPeriodSec(rateLimitRule.getWarmUpPeriodSec());
        }

        // Set timeout if strategy is rate limiter
        if (rateLimitRule.getControlBehavior().equals(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER)) {
            flowRule.setMaxQueueingTimeMs(rateLimitRule.getTimeoutInSec() * 1000);
        }

        return flowRule;
    }

    /**
     * Convert FlowRule to RateLimitRule.
     */
    private RateLimitRule convertToRateLimitRule(FlowRule flowRule) {
        return RateLimitRule.builder()
                .resource(flowRule.getResource())
                .limitApp(flowRule.getLimitApp())
                .grade(flowRule.getGrade())
                .count((long) flowRule.getCount())  // Fixed: cast double to long
                .strategy(flowRule.getStrategy())
                .controlBehavior(flowRule.getControlBehavior())
                .warmUpPeriodSec(flowRule.getWarmUpPeriodSec())  // Fixed: returns int directly
                .timeoutInSec(flowRule.getMaxQueueingTimeMs() / 1000)
                .clusterMode(flowRule.isClusterMode())
                .build();
    }

    /**
     * Persist rules to JSON file.
     */
    private void persistRulesToFile(List<FlowRule> flowRules) {
        try {
            File file = new File(RULES_FILE_PATH);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, flowRules);
            log.info("Persisted {} flow rules to {}", flowRules.size(), RULES_FILE_PATH);
        } catch (IOException e) {
            log.error("Failed to persist flow rules to file: {}", RULES_FILE_PATH, e);
        }
    }
}
