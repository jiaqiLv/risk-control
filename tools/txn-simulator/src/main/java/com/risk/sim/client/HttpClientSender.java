package com.risk.sim.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for sending transaction requests to the target service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpClientSender {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private WebClient webClient;
    private String baseUrl;
    private String endpoint;
    private int timeoutMs;

    /**
     * Initialize the HTTP client with configuration.
     *
     * @param baseUrl   Base URL of the target service
     * @param endpoint  Endpoint path
     * @param timeoutMs Request timeout in milliseconds
     */
    public void initialize(String baseUrl, String endpoint, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;

        this.webClient = webClientBuilder
                .baseUrl(this.baseUrl)
                .build();

        log.info("Initialized HTTP client: {}{}, timeout={}ms", this.baseUrl, this.endpoint, this.timeoutMs);
    }

    /**
     * Send a transaction request.
     *
     * @param requestBody Request body as Map
     * @return Response from the service
     * @throws Exception if request fails
     */
    public SimulationResponse send(Map<String, Object> requestBody) throws Exception {
        if (webClient == null) {
            throw new IllegalStateException("HTTP client not initialized. Call initialize() first.");
        }

        long startTime = System.currentTimeMillis();
        String requestJson;

        log.info("Sending request to {}{}", this.baseUrl, this.endpoint);
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
            log.info("Request JSON: {}", requestJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request", e);
            throw new Exception("Request serialization failed", e);
        }

        try {
            String responseBody = webClient
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorMap(WebClientResponseException.class, ex -> {
                        log.error("HTTP request failed: status={}, body={}",
                                ex.getStatusCode(), ex.getResponseBodyAsString());
                        return new Exception("HTTP error: " + ex.getStatusCode());
                    })
                    .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> {
                        log.error("HTTP request timeout after {}ms", timeoutMs);
                        return new Exception("Request timeout");
                    })
                    .block();

            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            // Parse response
            Map<String, Object> responseMap = objectMapper.readValue(
                    responseBody, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            return SimulationResponse.builder()
                    .success(true)
                    .latencyMs(latency)
                    .statusCode(HttpStatus.OK.value())
                    .responseBody(responseMap)
                    .errorMessage(null)
                    .build();

        } catch (WebClientResponseException e) {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            return SimulationResponse.builder()
                    .success(false)
                    .latencyMs(latency)
                    .statusCode(e.getStatusCode().value())
                    .responseBody(null)
                    .errorMessage("HTTP error: " + e.getStatusCode())
                    .build();
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            return SimulationResponse.builder()
                    .success(false)
                    .latencyMs(latency)
                    .statusCode(-1)
                    .responseBody(null)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Send a transaction request asynchronously.
     *
     * @param requestBody Request body as Map
     * @return Mono containing the response
     */
    public Mono<SimulationResponse> sendAsync(Map<String, Object> requestBody) {
        if (webClient == null) {
            return Mono.error(new IllegalStateException("HTTP client not initialized"));
        }

        long startTime = System.currentTimeMillis();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(requestBody))
                .flatMap(requestJson ->
                        webClient
                                .post()
                                .uri(endpoint)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(requestJson)
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofMillis(timeoutMs))
                                .map(responseBody -> {
                                    long latency = System.currentTimeMillis() - startTime;
                                    try {
                                        Map<String, Object> responseMap = objectMapper.readValue(
                                                responseBody, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                                        return SimulationResponse.builder()
                                                .success(true)
                                                .latencyMs(latency)
                                                .statusCode(HttpStatus.OK.value())
                                                .responseBody(responseMap)
                                                .errorMessage(null)
                                                .build();
                                    } catch (JsonProcessingException e) {
                                        return SimulationResponse.builder()
                                                .success(false)
                                                .latencyMs(latency)
                                                .statusCode(HttpStatus.OK.value())
                                                .responseBody(null)
                                                .errorMessage("Failed to parse response")
                                                .build();
                                    }
                                })
                                .onErrorResume(WebClientResponseException.class, e -> {
                                    long latency = System.currentTimeMillis() - startTime;
                                    return Mono.just(SimulationResponse.builder()
                                            .success(false)
                                            .latencyMs(latency)
                                            .statusCode(e.getStatusCode().value())
                                            .responseBody(null)
                                            .errorMessage("HTTP error: " + e.getStatusCode())
                                            .build());
                                })
                                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                                    long latency = System.currentTimeMillis() - startTime;
                                    return Mono.just(SimulationResponse.builder()
                                            .success(false)
                                            .latencyMs(latency)
                                            .statusCode(-1)
                                            .responseBody(null)
                                            .errorMessage("Timeout")
                                            .build());
                                })
                );
    }

    /**
     * Health check for the target service.
     *
     * @return true if service is healthy, false otherwise
     */
    public boolean healthCheck() {
        try {
            String response = webClient
                    .get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(5000))
                    .block();

            return response != null && !response.isEmpty();
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }
}
