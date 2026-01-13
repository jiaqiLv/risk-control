package com.risk.gateway.controller;

import com.risk.gateway.model.TransactionRequest;
import com.risk.gateway.model.TransactionResponse;
import com.risk.gateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for gateway endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    /**
     * Process a single transaction.
     *
     * @param request Transaction request
     * @return Transaction response
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request) {

        log.info("Received transaction request: transactionId={}, userId={}, amount={}",
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount());

        TransactionResponse response = gatewayService.processTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Batch process transactions (placeholder for future implementation).
     *
     * @param requests List of transaction requests
     * @return List of transaction responses
     */
    @PostMapping("/transactions/batch")
    public ResponseEntity<?> processBatch(
            @Valid @RequestBody java.util.List<TransactionRequest> requests) {

        log.info("Received batch transaction request: count={}", requests.size());

        // TODO: Implement batch processing
        return ResponseEntity.ok(Map.of(
                "message", "Batch processing not yet implemented",
                "count", requests.size()
        ));
    }

    /**
     * Health check endpoint.
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean orchestratorHealthy = gatewayService.checkOrchestratorHealth();

        Map<String, Object> health = Map.of(
                "status", orchestratorHealthy ? "UP" : "DEGRADED",
                "orchestrator", orchestratorHealthy ? "UP" : "DOWN",
                "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(health);
    }

    /**
     * Service info endpoint.
     *
     * @return Service information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> info = Map.of(
                "service", "gateway-service",
                "version", "1.0.0-SNAPSHOT",
                "description", "Risk Control Gateway Service - Unified API Gateway"
        );

        return ResponseEntity.ok(info);
    }
}
