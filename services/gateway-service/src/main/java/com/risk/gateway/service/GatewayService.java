package com.risk.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.gateway.config.GatewayProperties;
import com.risk.gateway.model.TransactionRequest;
import com.risk.gateway.model.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Gateway service that forwards requests to the orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final WebClient orchestratorWebClient;
    private final ObjectMapper objectMapper;
    private final GatewayProperties properties;

    /**
     * Process transaction request by forwarding to orchestrator.
     *
     * @param request Transaction request
     * @return Transaction response
     */
    public TransactionResponse processTransaction(TransactionRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.info("Processing transaction: transactionId={}, requestId={}",
                request.getTransactionId(), requestId);

        long startTime = System.currentTimeMillis();

        try {
            if (properties.isLogRequests()) {
                log.debug("Forwarding request to orchestrator: {}",
                        objectMapper.writeValueAsString(request));
            }

            // Forward request to orchestrator
            TransactionResponse response = orchestratorWebClient
                    .post()
                    .uri("/api/v1/evaluate")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TransactionResponse.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .block();

            long latency = System.currentTimeMillis() - startTime;
            log.info("Transaction processed: transactionId={}, decision={}, latency={}ms",
                    request.getTransactionId(),
                    response != null ? response.getDecision() : "null",
                    latency);

            if (response != null) {
                response.setRequestId(requestId);
                response.setTransactionId(request.getTransactionId());
                response.setProcessedAt(System.currentTimeMillis());
            }

            if (properties.isLogResponses() && response != null) {
                log.debug("Orchestrator response: {}",
                        objectMapper.writeValueAsString(response));
            }

            return response;

        } catch (WebClientResponseException e) {
            log.error("Orchestrator returned error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());

            return TransactionResponse.builder()
                    .requestId(requestId)
                    .transactionId(request.getTransactionId())
                    .decision("REVIEW")
                    .riskScore(0.5)
                    .reasons(List.of("Service unavailable"))
                    .processedAt(System.currentTimeMillis())
                    .statusCode(e.getStatusCode().value())
                    .message("Orchestrator service error: " + e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process transaction: transactionId={}, error={}",
                    request.getTransactionId(), e.getMessage());

            return TransactionResponse.builder()
                    .requestId(requestId)
                    .transactionId(request.getTransactionId())
                    .decision("REVIEW")
                    .riskScore(0.5)
                    .reasons(List.of("Processing error"))
                    .processedAt(System.currentTimeMillis())
                    .statusCode(500)
                    .message("Internal error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Health check by calling orchestrator.
     *
     * @return true if orchestrator is healthy, false otherwise
     */
    public boolean checkOrchestratorHealth() {
        try {
            String response = orchestratorWebClient
                    .get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .block();

            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.warn("Orchestrator health check failed: {}", e.getMessage());
            return false;
        }
    }
}
