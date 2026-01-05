package com.riskcontrol.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskcontrol.common.error.BusinessException;
import com.riskcontrol.common.error.ErrorCode;
import com.riskcontrol.common.util.IdGenerator;
import com.riskcontrol.gateway.client.FeatureClient;
import com.riskcontrol.gateway.client.GraphClient;
import com.riskcontrol.gateway.client.ModelClient;
import com.riskcontrol.gateway.model.Decision;
import com.riskcontrol.gateway.model.RiskScoreRequest;
import com.riskcontrol.gateway.model.RiskScoreResponse;
import com.riskcontrol.gateway.repository.RiskAuditRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.github.resilience4j.core.IntervalFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class RiskScoringService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    private final GraphClient graphClient;
    private final FeatureClient featureClient;
    private final ModelClient modelClient;
    private final RiskAuditRepository auditRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Thresholds (can be loaded from DB)
    private static final double REJECT_THRESHOLD = 0.8;
    private static final double REVIEW_THRESHOLD = 0.5;

    public RiskScoringService(GraphClient graphClient, FeatureClient featureClient,
                            ModelClient modelClient, RiskAuditRepository auditRepository,
                            @Qualifier("reactiveRedisTemplate")
                            ReactiveRedisTemplate<String, String> redisTemplate,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.graphClient = graphClient;
        this.featureClient = featureClient;
        this.modelClient = modelClient;
        this.auditRepository = auditRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<RiskScoreResponse> calculateRiskScore(RiskScoreRequest request) {
        String requestId = IdGenerator.generateRequestId();
        MDC.put("traceId", requestId);

        long startTime = System.currentTimeMillis();

        return checkIdempotency(request.getEventId(), requestId)
                .switchIfEmpty(Mono.defer(() -> processRequest(request, requestId, startTime)))
                .doFinally(signal -> MDC.clear());
    }

    private Mono<RiskScoreResponse> checkIdempotency(String eventId, String requestId) {
        String dedupKey = "dedup:event:" + eventId;

        return redisTemplate.opsForValue().get(dedupKey)
                .flatMap(cachedResponse -> {
                    try {
                        RiskScoreResponse response = objectMapper.readValue(cachedResponse, RiskScoreResponse.class);
                        log.info("Returning cached response for eventId: {}", eventId);
                        return Mono.just(response.toBuilder().cached(true).build());
                    } catch (Exception e) {
                        log.error("Error deserializing cached response", e);
                        return Mono.empty();
                    }
                });
    }

    private Mono<RiskScoreResponse> processRequest(RiskScoreRequest request, String requestId, long startTime) {
        log.info("Processing risk scoring request: requestId={}, eventId={}, subjectType={}, subjectId={}",
                requestId, request.getEventId(), request.getSubjectType(), request.getSubjectId());

        // Step 1: Get subgraph from graph service
        return sampleSubgraphWithFallback(request, requestId)
                .flatMap(subgraph -> {
                    log.debug("Graph sampling completed for requestId: {}", requestId);

                    // Step 2: Get features from feature service
                    return getFeaturesWithFallback(request, requestId)
                            .flatMap(features -> {
                                log.debug("Feature retrieval completed for requestId: {}", requestId);

                                // Step 3: Get model prediction
                                return predictWithFallback(request, features, requestId)
                                        .flatMap(modelResponse -> {
                                            log.debug("Model prediction completed for requestId: {}", requestId);

                                            // Step 4: Make decision
                                            Decision decision = makeDecision(modelResponse.getFraudScore());

                                            RiskScoreResponse response = RiskScoreResponse.builder()
                                                    .requestId(requestId)
                                                    .eventId(request.getEventId())
                                                    .subjectType(request.getSubjectType())
                                                    .subjectId(request.getSubjectId())
                                                    .fraudScore(modelResponse.getFraudScore())
                                                    .decision(decision.name())
                                                    .reason(buildReason(decision, modelResponse.getFraudScore()))
                                                    .modelVersion(modelResponse.getModelVersion())
                                                    .processingTimeMs(System.currentTimeMillis() - startTime)
                                                    .cached(false)
                                                    .build();

                                            // Step 5: Audit and publish event (fire and forget)
                                            return auditAndPublish(request, response)
                                                    .thenReturn(response)
                                                    .doOnNext(resp -> cacheResponse(request.getEventId(), resp));
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error processing risk scoring request: {}", requestId, error);

                    // Fallback to rule-based decision
                    return Mono.just(fallbackDecision(request, requestId, startTime, error));
                });
    }

    @CircuitBreaker(name = "graphService", fallbackMethod = "graphServiceFallback")
    @TimeLimiter(name = "graphService")
    private Mono<fraud.graph.v1.SampleSubgraphResponse> sampleSubgraphWithFallback(RiskScoreRequest request, String requestId) {
        fraud.graph.v1.SampleSubgraphRequest graphRequest =
                fraud.graph.v1.SampleSubgraphRequest.newBuilder()
                        .addSeedNodeIds(request.getSubjectId())
                        .setHops(2)
                        .setNeighborLimit(50)
                        .setStartTimeMs(System.currentTimeMillis() - 86400000L) // Last 24 hours
                        .setEndTimeMs(System.currentTimeMillis())
                        .build();

        return graphClient.sampleSubgraph(graphRequest);
    }

    private Mono<fraud.graph.v1.SampleSubgraphResponse> graphServiceFallback(RiskScoreRequest request, String requestId,
                                                                            Exception ex) {
        log.warn("Graph service failed, using empty subgraph for requestId: {}", requestId);
        return Mono.just(fraud.graph.v1.SampleSubgraphResponse.newBuilder()
                .setSnapshotTimeMs(System.currentTimeMillis())
                .setLatencyMs(0)
                .build());
    }

    @CircuitBreaker(name = "featureService", fallbackMethod = "featureServiceFallback")
    @TimeLimiter(name = "featureService")
    private Mono<fraud.feature.v1.GetFeaturesResponse> getFeaturesWithFallback(RiskScoreRequest request, String requestId) {
        fraud.feature.v1.GetFeaturesRequest featureRequest =
                fraud.feature.v1.GetFeaturesRequest.newBuilder()
                        .setRequestId(requestId)
                        .setSubjectType(request.getSubjectType())
                        .setSubjectId(request.getSubjectId())
                        .addFeatureNames("transaction_count_24h")
                        .addFeatureNames("avg_transaction_amount_7d")
                        .addFeatureNames("account_age_days")
                        .addFeatureNames("is_new_device")
                        .addFeatureNames("risk_score_30d")
                        .setFeatureVersion(request.getModelVersion() != null ? request.getModelVersion() : "v1")
                        .build();

        return featureClient.getFeatures(featureRequest);
    }

    private Mono<fraud.feature.v1.GetFeaturesResponse> featureServiceFallback(RiskScoreRequest request, String requestId,
                                                                              Exception ex) {
        log.warn("Feature service failed, using minimal features for requestId: {}", requestId);
        return Mono.just(fraud.feature.v1.GetFeaturesResponse.newBuilder()
                .setRequestId(requestId)
                .setCacheHit(false)
                .build());
    }

    @CircuitBreaker(name = "modelService", fallbackMethod = "modelServiceFallback")
    @TimeLimiter(name = "modelService")
    private Mono<fraud.model.v1.PredictResponse> predictWithFallback(
            RiskScoreRequest request,
            fraud.feature.v1.GetFeaturesResponse features,
            String requestId) {

        Map<String, String> featureMap = new HashMap<>();
        for (fraud.feature.v1.FeatureValue feature : features.getFeaturesList()) {
            featureMap.put(feature.getName(), getFeatureValueAsString(feature));
        }

        // Add request-specific features
        featureMap.put("transaction_amount", request.getTransactionAmount().toString());

        fraud.model.v1.PredictRequest modelRequest =
                fraud.model.v1.PredictRequest.newBuilder()
                        .setRequestId(requestId)
                        .setSubjectType(request.getSubjectType())
                        .setSubjectId(request.getSubjectId())
                        .putAllFeatures(featureMap)
                        .setModelVersion(request.getModelVersion() != null ? request.getModelVersion() : "v1")
                        .build();

        return modelClient.predict(modelRequest);
    }

    private Mono<fraud.model.v1.PredictResponse> modelServiceFallback(
            RiskScoreRequest request,
            fraud.feature.v1.GetFeaturesResponse features,
            String requestId,
            Exception ex) {

        log.warn("Model service failed for requestId: {}, using rule-based fallback", requestId);

        // Rule-based fallback
        double fallbackScore = calculateRuleBasedScore(request, features);

        return Mono.just(fraud.model.v1.PredictResponse.newBuilder()
                .setRequestId(requestId)
                .setFraudScore(fallbackScore)
                .setModelVersion("rule_based_fallback_v1")
                .setInferenceTimeMs(0)
                .build());
    }

    private double calculateRuleBasedScore(RiskScoreRequest request, fraud.feature.v1.GetFeaturesResponse features) {
        double score = 0.1; // Base score

        // Simple rules
        if (request.getTransactionAmount() != null && request.getTransactionAmount() > 10000) {
            score += 0.2;
        }

        if (request.getIpAddress() != null) {
            // Could add IP reputation check here
        }

        return Math.min(score, 1.0);
    }

    private Decision makeDecision(double fraudScore) {
        if (fraudScore >= REJECT_THRESHOLD) {
            return Decision.REJECT;
        } else if (fraudScore >= REVIEW_THRESHOLD) {
            return Decision.REVIEW;
        } else {
            return Decision.ALLOW;
        }
    }

    private String buildReason(Decision decision, double score) {
        return String.format("Fraud score: %.2f, Decision: %s", score, decision.name());
    }

    private RiskScoreResponse fallbackDecision(RiskScoreRequest request, String requestId,
                                              long startTime, Throwable error) {
        log.error("All fallbacks exhausted for requestId: {}, using safe default", requestId);

        return RiskScoreResponse.builder()
                .requestId(requestId)
                .eventId(request.getEventId())
                .subjectType(request.getSubjectType())
                .subjectId(request.getSubjectId())
                .fraudScore(0.3)
                .decision(Decision.REVIEW.name())
                .reason("Service error - manual review required: " + error.getMessage())
                .modelVersion("fallback_v1")
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .cached(false)
                .build();
    }

    private Mono<Void> auditAndPublish(RiskScoreRequest request, RiskScoreResponse response) {
        try {
            // Save to database
            String requestBody = objectMapper.writeValueAsString(request);
            auditRepository.saveAudit(response, requestBody)
                    .doOnSuccess(id -> log.debug("Audit saved with ID: {}", id))
                    .onErrorResume(error -> {
                        log.error("Failed to save audit", error);
                        return Mono.empty();
                    })
                    .subscribe();

            // Publish to Kafka
            publishKafkaEvent(request, response);

        } catch (Exception e) {
            log.error("Error in audit/publish", e);
        }

        return Mono.empty();
    }

    private void publishKafkaEvent(RiskScoreRequest request, RiskScoreResponse response) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("request_id", response.getRequestId());
            event.put("event_id", response.getEventId());
            event.put("subject_type", response.getSubjectType());
            event.put("subject_id", response.getSubjectId());
            event.put("fraud_score", response.getFraudScore());
            event.put("decision", response.getDecision());
            event.put("timestamp", System.currentTimeMillis());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("risk_score_events", response.getEventId(), eventJson);
            log.debug("Published Kafka event for requestId: {}", response.getRequestId());

        } catch (Exception e) {
            log.error("Failed to publish Kafka event", e);
        }
    }

    private void cacheResponse(String eventId, RiskScoreResponse response) {
        try {
            String dedupKey = "dedup:event:" + eventId;
            String responseJson = objectMapper.writeValueAsString(response);

            redisTemplate.opsForValue()
                    .set(dedupKey, responseJson, java.time.Duration.ofMinutes(10))
                    .subscribe();

        } catch (Exception e) {
            log.error("Failed to cache response", e);
        }
    }

    private String getFeatureValueAsString(fraud.feature.v1.FeatureValue feature) {
        if (feature.hasStringValue()) {
            return feature.getStringValue();
        } else if (feature.hasIntValue()) {
            return String.valueOf(feature.getIntValue());
        } else if (feature.hasDoubleValue()) {
            return String.valueOf(feature.getDoubleValue());
        } else if (feature.hasBoolValue()) {
            return String.valueOf(feature.getBoolValue());
        }
        return "";
    }
}
