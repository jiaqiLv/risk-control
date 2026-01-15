package com.risk.gateway.controller;

import com.risk.gateway.model.RateLimitRule;
import com.risk.gateway.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for rate limit rule management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimitService rateLimitService;

    /**
     * Get all rate limit rules.
     *
     * GET /api/v1/rate-limit/rules
     *
     * @return List of all rate limit rules
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getAllRules() {
        log.info("Fetching all rate limit rules");

        List<RateLimitRule> rules = rateLimitService.getAllRules();

        Map<String, Object> response = Map.of(
                "success", true,
                "count", rules.size(),
                "rules", rules
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get rate limit rule by resource.
     *
     * GET /api/v1/rate-limit/rules/{resource}
     *
     * @param resource Resource name (API endpoint path)
     * @return Rate limit rule for the specified resource
     */
    @GetMapping("/rules/{resource}")
    public ResponseEntity<Map<String, Object>> getRuleByResource(@PathVariable String resource) {
        log.info("Fetching rate limit rule for resource: {}", resource);

        RateLimitRule rule = rateLimitService.getRuleByResource(resource);

        if (rule == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Rule not found for resource: " + resource
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "rule", rule
        ));
    }

    /**
     * Create or update a rate limit rule.
     *
     * POST /api/v1/rate-limit/rules
     *
     * Request body example:
     * {
     *   "resource": "/api/v1/transactions",
     *   "grade": 1,
     *   "count": 100,
     *   "strategy": 0,
     *   "controlBehavior": 0,
     *   "description": "Limit transactions to 100 QPS"
     * }
     *
     * @param rule Rate limit rule to create or update
     * @return Updated list of all rules
     */
    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> createOrUpdateRule(
            @Valid @RequestBody RateLimitRule rule) {

        log.info("Creating/updating rate limit rule for resource: {}, count: {}",
                rule.getResource(), rule.getCount());

        List<RateLimitRule> updatedRules = rateLimitService.addOrUpdateRule(rule);

        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Rule created/updated successfully",
                "rule", rule,
                "totalRules", updatedRules.size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a rate limit rule.
     *
     * DELETE /api/v1/rate-limit/rules/{resource}
     *
     * @param resource Resource name (API endpoint path)
     * @return Updated list of all rules
     */
    @DeleteMapping("/rules/{resource}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String resource) {
        log.info("Deleting rate limit rule for resource: {}", resource);

        List<RateLimitRule> updatedRules = rateLimitService.deleteRule(resource);

        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Rule deleted successfully",
                "resource", resource,
                "totalRules", updatedRules.size()
        );

        return ResponseEntity.ok(response);
    }
}
