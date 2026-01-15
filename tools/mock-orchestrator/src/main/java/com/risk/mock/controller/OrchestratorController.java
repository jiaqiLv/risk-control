package com.risk.mock.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Mock Orchestrator Controller.
 * Simulates the real orchestrator service for testing.
 * Matches Gateway Service models: TransactionRequest and TransactionResponse.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class OrchestratorController {

    /**
     * Evaluate transaction request.
     * POST /api/v1/evaluate
     *
     * Request matches: com.risk.gateway.model.TransactionRequest
     * Response matches: com.risk.gateway.model.TransactionResponse
     */
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@RequestBody Map<String, Object> request) {
        // Extract required fields
        String transactionId = (String) request.get("transactionId");
        String userId = (String) request.get("userId");
        Object amountObj = request.get("transactionAmt");

        // Extract optional fields for logging (from attributes map)
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) request.get("attributes");
        String deviceType = attributes != null ? (String) attributes.get("deviceType") : null;
        String card4 = attributes != null ? (String) attributes.get("card4") : null;
        String card6 = attributes != null ? (String) attributes.get("card6") : null;
        String dist2 = attributes != null ? (String) attributes.get("dist2") : null;
        String productCd = (String) request.get("productCd");

        log.info("Received evaluation request: transactionId={}, userId={}, amount={}, deviceType={}, productCd={}",
                transactionId, userId, amountObj, deviceType, productCd);

        // Simulate processing time (10-50ms)
        try {
            Thread.sleep(10 + (long) (Math.random() * 40));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate mock decision based on transaction amount
        String decision;
        double riskScore;
        List<String> reasons;
        Integer statusCode = 200;
        String message = "Transaction processed successfully";

        if (amountObj instanceof Number) {
            double amount = ((Number) amountObj).doubleValue();

            if (amount > 100) {
                // Large amount - high risk
                decision = "REVIEW";
                riskScore = 0.8 + Math.random() * 0.15; // 0.8-0.95
                reasons = Arrays.asList("Large transaction amount", "High amount threshold exceeded");
                statusCode = 200;
                message = "Transaction flagged for review due to high amount";
            } else if (amount > 10) {
                // Medium amount - medium risk
                if (Math.random() > 0.5) {
                    decision = "REVIEW";
                    riskScore = 0.4 + Math.random() * 0.3; // 0.4-0.7
                    reasons = Arrays.asList("Medium transaction amount", "Additional verification required");
                    message = "Transaction requires manual review";
                } else {
                    decision = "APPROVE";
                    riskScore = 0.3 + Math.random() * 0.2; // 0.3-0.5
                    reasons = Collections.emptyList();
                    message = "Transaction approved";
                }
            } else {
                // Small amount - low risk
                if (Math.random() > 0.95) {
                    // 5% chance of review for small amounts
                    decision = "REVIEW";
                    riskScore = Math.random() * 0.3; // 0.0-0.3
                    reasons = Arrays.asList("Unusual pattern detected");
                    message = "Transaction flagged for pattern review";
                } else {
                    decision = "APPROVE";
                    riskScore = Math.random() * 0.2; // 0.0-0.2
                    reasons = Collections.emptyList();
                    message = "Transaction approved";
                }
            }
        } else {
            // No amount - default to approve
            decision = "APPROVE";
            riskScore = 0.1;
            reasons = Collections.emptyList();
            message = "Transaction approved (no amount specified)";
        }

        // Build response matching TransactionResponse model
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", UUID.randomUUID().toString());
        response.put("transactionId", transactionId);
        response.put("decision", decision);
        response.put("riskScore", riskScore);
        response.put("reasons", reasons);
        response.put("processedAt", Instant.now().toEpochMilli());
        response.put("statusCode", statusCode);
        response.put("message", message);

        log.info("Returning decision: transactionId={}, decision={}, riskScore={}, statusCode={}",
                transactionId, decision, riskScore, statusCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     * GET /actuator/health
     */
    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        return ResponseEntity.ok(health);
    }

    /**
     * Service info endpoint.
     * GET /info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> info = new HashMap<>();
        info.put("service", "mock-orchestrator");
        info.put("version", "1.0.0");
        info.put("description", "Mock Orchestrator Service for Testing");
        return ResponseEntity.ok(info);
    }
}
