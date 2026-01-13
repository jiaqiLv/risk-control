package com.risk.orch.controller;

import com.risk.orch.model.OrchestratorRequest;
import com.risk.orch.model.OrchestratorResponse;
import com.risk.orch.service.OrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for orchestrator endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    /**
     * Evaluate transaction risk.
     * This is the main endpoint called by Gateway Service.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<OrchestratorResponse> evaluate(
            @Valid @RequestBody OrchestratorRequest request) {

        log.info("Received evaluation request: transactionId={}, userId={}, amount={}",
                request.getTransactionId(), request.getUserId(), request.getAmount());

        OrchestratorResponse response = orchestratorService.evaluate(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "orchestrator-service",
                "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(health);
    }

    /**
     * Service info endpoint.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> info = Map.of(
                "service", "orchestrator-service",
                "version", "1.0.0-SNAPSHOT",
                "description", "Risk Orchestrator Service - Coordinates feature, decision, and model services"
        );

        return ResponseEntity.ok(info);
    }
}
