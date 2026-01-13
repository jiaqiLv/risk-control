package com.risk.gateway.controller;

import com.risk.gateway.model.TransactionRequest;
import com.risk.gateway.model.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Test controller for standalone testing without orchestrator.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    /**
     * Test endpoint that returns a mock response.
     * This can be used for testing gateway without orchestrator.
     */
    @PostMapping("/mock")
    public TransactionResponse mockEvaluation(@RequestBody TransactionRequest request) {
        log.info("Mock evaluation request: {}", request.getTransactionId());

        // Simple mock logic
        String decision = request.getAmount() > 1000 ? "REVIEW" : "APPROVE";
        double riskScore = request.getAmount() > 1000 ? 0.65 : 0.15;

        return TransactionResponse.builder()
                .requestId("mock-" + request.getTransactionId())
                .transactionId(request.getTransactionId())
                .decision(decision)
                .riskScore(riskScore)
                .reasons(decision.equals("REVIEW") ?
                        List.of("High amount transaction") : List.of("Normal transaction"))
                .processedAt(System.currentTimeMillis())
                .statusCode(200)
                .message("Mock response - orchestrator not required")
                .build();
    }

    /**
     * Health check for test endpoints.
     */
    @GetMapping("/health")
    public Map<String, String> testHealth() {
        return Map.of("status", "UP", "message", "Test endpoints are working");
    }
}
