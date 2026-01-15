package com.risk.gateway.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel configuration for rate limiting and circuit breaking.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SentinelConfig {

    private final ObjectMapper objectMapper;

    /**
     * Initialize flow rules from JSON file on startup.
     */
    @PostConstruct
    public void initFlowRules() {
        try {
            // Load rules from sentinel-rules.json
            InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("sentinel-rules.json");

            if (inputStream != null) {
                List<FlowRule> rules = objectMapper.readValue(inputStream,
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, FlowRule.class));

                FlowRuleManager.loadRules(rules);
                log.info("Loaded {} flow rules from sentinel-rules.json", rules.size());
            } else {
                log.warn("sentinel-rules.json not found, using default rules");
                loadDefaultRules();
            }
        } catch (IOException e) {
            log.error("Failed to load flow rules from file, using defaults", e);
            loadDefaultRules();
        }

        // Initialize degrade rules
        initDegradeRules();
    }

    /**
     * Initialize degrade (circuit breaker) rules.
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // Exception ratio circuit breaker for /api/v1/transactions
        DegradeRule exceptionRule = new DegradeRule();
        exceptionRule.setResource("/api/v1/transactions");
        exceptionRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        exceptionRule.setCount(0.5); // 50% exception ratio
        exceptionRule.setTimeWindow(10); // 10 seconds recovery
        exceptionRule.setMinRequestAmount(5); // At least 5 requests
        exceptionRule.setStatIntervalMs(1000); // 1 second statistics
        rules.add(exceptionRule);

        // Slow call ratio circuit breaker for /api/v1/transactions
        // Note: Sentinel 1.8.6 uses DEGRADE_GRADE_RT for slow request ratio
        DegradeRule slowRule = new DegradeRule();
        slowRule.setResource("/api/v1/transactions");
        slowRule.setGrade(RuleConstant.DEGRADE_GRADE_RT); // Fixed: use RT instead of SLOW_REQUEST_RATIO
        slowRule.setCount(3000); // 3 seconds max response time
        slowRule.setSlowRatioThreshold(0.5); // 50% slow calls
        slowRule.setTimeWindow(10); // 10 seconds recovery
        slowRule.setMinRequestAmount(5); // At least 5 requests
        slowRule.setStatIntervalMs(1000); // 1 second statistics
        rules.add(slowRule);

        DegradeRuleManager.loadRules(rules);
        log.info("Loaded {} degrade (circuit breaker) rules", rules.size());
    }

    /**
     * Load default flow rules if file loading fails.
     */
    private void loadDefaultRules() {
        List<FlowRule> rules = new ArrayList<>();

        // QPS rule for /api/v1/transactions endpoint
        FlowRule rule1 = new FlowRule();
        rule1.setResource("/api/v1/transactions");
        rule1.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule1.setCount(1000); // 1000 QPS
        rule1.setStrategy(RuleConstant.STRATEGY_DIRECT);
        rule1.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rule1.setLimitApp("default");
        rules.add(rule1);

        // Thread count rule for /api/v1/transactions (concurrent limit)
        FlowRule rule3 = new FlowRule();
        rule3.setResource("/api/v1/transactions");
        rule3.setGrade(RuleConstant.FLOW_GRADE_THREAD);
        rule3.setCount(200); // 200 concurrent threads
        rule3.setStrategy(RuleConstant.STRATEGY_DIRECT);
        rule3.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP); // Fixed: use WARM_UP instead of THROTTLING
        rule3.setLimitApp("default");
        rules.add(rule3);

        FlowRuleManager.loadRules(rules);
        log.info("Loaded {} default flow rules", rules.size());
    }
}
