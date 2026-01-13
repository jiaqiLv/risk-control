package com.risk.orch.service;

import com.risk.contracts.build.*;
import com.risk.data.service.TransactionService;
import com.risk.orch.config.OrchestratorProperties;
import com.risk.orch.model.OrchestratorRequest;
import com.risk.orch.model.OrchestratorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core orchestrator service that coordinates calls to downstream services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final WebClient featureServiceWebClient;
    private final WebClient decisionServiceWebClient;
    private final RiskInfraServiceGrpc.RiskInfraServiceBlockingStub grpcStub;
    private final OrchestratorProperties properties;
    private final TransactionService transactionService;

    /**
     * Process transaction evaluation request.
     */
    public OrchestratorResponse evaluate(OrchestratorRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        log.info("Evaluating transaction: transactionId={}, requestId={}, mode={}",
                request.getTransactionId(), requestId, properties.getMode());

        try {
            // Check if in mock mode
            if (properties.isMockMode()) {
                return evaluateMock(request, requestId);
            }

            var responseBuilder = OrchestratorResponse.builder()
                    .transactionId(request.getTransactionId())
                    .processedAt(System.currentTimeMillis());

            List<String> allReasons = new ArrayList<>();
            List<String> rulesTriggered = new ArrayList<>();
            String fallbackUsed = null;

            // Step 1: Get features (optional, can be skipped)
            Map<String, Object> features = null;
            try {
                if (properties.getMode() != OrchestratorProperties.Mode.MODEL_ONLY) {
                    features = getFeatures(request);
                }
            } catch (Exception e) {
                log.warn("Failed to get features: {}", e.getMessage());
                allReasons.add("features_unavailable");
            }

            // Step 2: Decision from rules
            Double ruleScore = null;
            String ruleDecision = null;

            if (properties.getMode() != OrchestratorProperties.Mode.MODEL_ONLY) {
                try {
                    Map<String, Object> ruleResult = evaluateRules(request, features);
                    ruleDecision = (String) ruleResult.get("decision");
                    ruleScore = (Double) ruleResult.get("score");
                    rulesTriggered = (List<String>) ruleResult.getOrDefault("triggeredRules", new ArrayList<>());

                    log.info("Rule evaluation: decision={}, score={}, rules={}",
                            ruleDecision, ruleScore, rulesTriggered);
                } catch (Exception e) {
                    log.warn("Rule evaluation failed: {}", e.getMessage());
                    allReasons.add("rules_failed");
                }
            }

            // Step 3: Decision from Python model (gRPC)
            Double modelScore = null;
            String modelDecision = null;

            if (properties.getMode() != OrchestratorProperties.Mode.RULES_ONLY) {
                try {
                    Map<String, Object> modelResult = callPythonModel(request);
                    modelScore = (Double) modelResult.get("riskScore");
                    modelDecision = (String) modelResult.get("decision");

                    log.info("Model evaluation: decision={}, score={}", modelDecision, modelScore);
                } catch (Exception e) {
                    log.error("Python model evaluation failed: {}", e.getMessage());

                    // Fallback to rules if configured
                    if (properties.isEnableFallback() && ruleDecision != null) {
                        fallbackUsed = "model_to_rules";
                        modelScore = ruleScore;
                        modelDecision = ruleDecision;
                        allReasons.add("model_fallback_to_rules");
                    } else {
                        allReasons.add("model_failed");
                    }
                }
            }

            // Step 4: Combine decisions
            String finalDecision;
            double finalScore;

            if (properties.getMode() == OrchestratorProperties.Mode.HYBRID) {
                // Hybrid mode: combine model and rules
                if (modelScore != null && ruleScore != null) {
                    // Weighted average (can be configured)
                    finalScore = (modelScore * 0.7) + (ruleScore * 0.3);
                    finalDecision = scoreToDecision(finalScore);
                    allReasons.add("hybrid_decision");
                    responseBuilder.modelUsed("hybrid");
                } else if (modelScore != null) {
                    finalScore = modelScore;
                    finalDecision = modelDecision != null ? modelDecision : scoreToDecision(finalScore);
                    responseBuilder.modelUsed("python_model");
                } else {
                    finalScore = ruleScore != null ? ruleScore : 0.5;
                    finalDecision = ruleDecision != null ? ruleDecision : "REVIEW";
                    responseBuilder.modelUsed("rules");
                }
            } else if (properties.getMode() == OrchestratorProperties.Mode.MODEL_ONLY) {
                finalScore = modelScore != null ? modelScore : 0.5;
                finalDecision = modelDecision != null ? modelDecision : "REVIEW";
                responseBuilder.modelUsed("python_model");
            } else {
                // RULES_ONLY
                finalScore = ruleScore != null ? ruleScore : 0.5;
                finalDecision = ruleDecision != null ? ruleDecision : "REVIEW";
                responseBuilder.modelUsed("rules");
            }

            // Build response
            responseBuilder.decision(finalDecision);
            responseBuilder.riskScore(finalScore);
            responseBuilder.reasons(allReasons);
            responseBuilder.rulesTriggered(rulesTriggered);
            responseBuilder.fallbackUsed(fallbackUsed);

            long latency = System.currentTimeMillis() - startTime;
            log.info("Evaluation completed: transactionId={}, decision={}, score={}, latency={}ms",
                    request.getTransactionId(), finalDecision, finalScore, latency);

            OrchestratorResponse response = responseBuilder.build();

            // Persist transaction data
            persistTransactionData(request, response);

            return response;

        } catch (Exception e) {
            log.error("Unexpected error during evaluation: {}", e.getMessage(), e);

            return OrchestratorResponse.builder()
                    .transactionId(request.getTransactionId())
                    .decision("REVIEW")
                    .riskScore(0.5)
                    .reasons(List.of("evaluation_error"))
                    .processedAt(System.currentTimeMillis())
                    .modelUsed("error")
                    .debugInfo(e.getMessage())
                    .build();
        }
    }

    /**
     * Get features from feature service (placeholder).
     */
    private Map<String, Object> getFeatures(OrchestratorRequest request) {
        // TODO: Implement feature service call
        log.debug("Getting features for transaction: {}", request.getTransactionId());

        // Mock features for now
        Map<String, Object> features = new HashMap<>();
        features.put("user_age_days", 180);
        features.put("txn_count_24h", 3);
        features.put("amount", request.getAmount());

        return features;
    }

    /**
     * Evaluate using decision rules (placeholder).
     */
    private Map<String, Object> evaluateRules(OrchestratorRequest request, Map<String, Object> features) {
        // TODO: Implement decision service call
        log.debug("Evaluating rules for transaction: {}", request.getTransactionId());

        // Simple rule logic for now
        double score = 0.3;
        String decision = "APPROVE";
        List<String> triggeredRules = new ArrayList<>();

        // Rule 1: High amount
        if (request.getAmount() > 1000) {
            score += 0.3;
            triggeredRules.add("high_amount");
        }

        // Rule 2: Very high amount
        if (request.getAmount() > 5000) {
            score += 0.4;
            triggeredRules.add("very_high_amount");
        }

        // Determine decision
        if (score >= properties.getRejectThreshold()) {
            decision = "REJECT";
        } else if (score >= properties.getReviewThreshold()) {
            decision = "REVIEW";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("decision", decision);
        result.put("score", score);
        result.put("triggeredRules", triggeredRules);

        return result;
    }

    /**
     * Call Python model via gRPC.
     */
    private Map<String, Object> callPythonModel(OrchestratorRequest request) {
        log.debug("Calling Python model for transaction: {}", request.getTransactionId());

        // Build gRPC request
        InferenceRequest grpcRequest = buildGrpcRequest(request);

        // Call gRPC service
        InferenceResponse response = grpcStub
                .withDeadlineAfter(properties.getPythonInferenceTimeoutMs(), TimeUnit.MILLISECONDS)
                .inference(grpcRequest);

        // Parse response
        String decision = response.getDecision().name();
        double score = response.getRiskScore();

        Map<String, Object> result = new HashMap<>();
        result.put("decision", decision);
        result.put("riskScore", score);

        if (response.getTopReasonsCount() > 0) {
            result.put("reasons", response.getTopReasonsList());
        }

        log.info("Python model response: decision={}, score={}", decision, score);

        return result;
    }

    /**
     * Build gRPC request from transaction request.
     */
    private InferenceRequest buildGrpcRequest(OrchestratorRequest request) {
        // Build transaction context
        TransactionContext.Builder txContextBuilder = TransactionContext.newBuilder()
                .setTransactionId(request.getTransactionId())
                .setUserId(request.getUserId())
                .setEventTimestampMs(request.getEventTimestamp())
                .setAmount(request.getAmount())
                .setCurrency(request.getCurrency())
                .setChannel(request.getChannel());

        if (request.getProductCd() != null) {
            txContextBuilder.setProductCd(request.getProductCd());
        }

        // Add attributes as features
        if (request.getAttributes() != null) {
            Map<String, FeatureValue> features = new HashMap<>();

            request.getAttributes().forEach((key, value) -> {
                if (value instanceof Number) {
                    FeatureValue fv = FeatureValue.newBuilder()
                            .setF64(((Number) value).doubleValue())
                            .build();
                    features.put(key, fv);
                } else if (value instanceof String) {
                    FeatureValue fv = FeatureValue.newBuilder()
                            .setStr((String) value)
                            .build();
                    features.put(key, fv);
                }
            });

            txContextBuilder.putAllAttributes(features);
        }

        // Build inference request
        InferenceRequest.Builder requestBuilder = InferenceRequest.newBuilder()
                .setRequestId("orch-" + request.getTransactionId())
                .setRequestTimestampMs(System.currentTimeMillis())
                .setModelName("fraud_detection_model")
                .setModelVersion("1.0.0")
                .setFeatureVersion("2026-01-11")
                .setTx(txContextBuilder.build());

        // Set inference options
        InferenceOptions.Builder optionsBuilder = InferenceOptions.newBuilder()
                .setMissingEmbeddingPolicy(InferenceOptions.MissingEmbeddingPolicy.MODEL_DEFAULT)
                .setAllowPythonFetch(true);

        requestBuilder.setOptions(optionsBuilder);

        return requestBuilder.build();
    }

    /**
     * Convert score to decision.
     */
    private String scoreToDecision(double score) {
        if (score >= properties.getRejectThreshold()) {
            return "REJECT";
        } else if (score >= properties.getReviewThreshold()) {
            return "REVIEW";
        } else {
            return "APPROVE";
        }
    }

    /**
     * Mock evaluation for testing without downstream services.
     */
    private OrchestratorResponse evaluateMock(OrchestratorRequest request, String requestId) {
        log.info("Using mock mode for evaluation");

        // Simple mock logic based on amount
        double score = 0.2;
        List<String> reasons = new ArrayList<>();
        String decision;

        if (request.getAmount() > 5000) {
            score = 0.75;
            decision = "REJECT";
            reasons.add("very_high_amount_mock");
        } else if (request.getAmount() > 1000) {
            score = 0.55;
            decision = "REVIEW";
            reasons.add("high_amount_mock");
        } else {
            score = 0.15;
            decision = "APPROVE";
            reasons.add("normal_amount_mock");
        }

        OrchestratorResponse response = OrchestratorResponse.builder()
                .transactionId(request.getTransactionId())
                .decision(decision)
                .riskScore(score)
                .reasons(reasons)
                .processedAt(System.currentTimeMillis())
                .modelUsed("mock")
                .debugInfo("Mock evaluation - no downstream services called")
                .build();

        // Persist transaction data even in mock mode
        persistTransactionData(request, response);

        return response;
    }

    /**
     * 持久化交易数据
     * 根据配置选择同步或异步方式
     */
    private void persistTransactionData(OrchestratorRequest request, OrchestratorResponse response) {
        try {
            // 准备交易数据
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("transactionId", request.getTransactionId());
            transactionData.put("userId", request.getUserId());
            transactionData.put("merchantId", extractMerchantId(request));
            transactionData.put("eventTimestamp", request.getEventTimestamp());
            transactionData.put("amount", request.getAmount());
            transactionData.put("productCd", request.getProductCd());
            transactionData.put("attributes", request.getAttributes());

            // 准备响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("transactionId", response.getTransactionId());
            responseData.put("decision", response.getDecision());
            responseData.put("riskScore", response.getRiskScore());
            responseData.put("modelUsed", response.getModelUsed());
            responseData.put("processedAt", response.getProcessedAt());
            responseData.put("reasons", response.getReasons());
            responseData.put("rulesTriggered", response.getRulesTriggered());

            // 根据配置选择同步或异步持久化
            if (properties.isAsyncPersistence()) {
                // 异步持久化 - 不阻塞主流程
                transactionService.saveAsync(transactionData);
                transactionService.updateDecisionAsync(response.getTransactionId(), responseData);
                log.debug("Transaction persistence scheduled (async): transactionId={}",
                        request.getTransactionId());
            } else {
                // 同步持久化 - 立即写入数据库
                try {
                    transactionService.saveSync(transactionData)
                            .timeout(Duration.ofSeconds(3))
                            .flatMap(saved -> {
                                log.debug("Transaction saved (sync): transactionId={}",
                                        saved.getTransactionId());
                                return transactionService.updateDecisionSync(
                                        response.getTransactionId(), responseData);
                            })
                            .block(Duration.ofSeconds(3));
                } catch (Exception e) {
                    log.error("Failed to persist transaction (sync): transactionId={}, error={}",
                            request.getTransactionId(), e.getMessage());
                    // 不影响主流程，即使持久化失败也返回响应
                }
            }
        } catch (Exception e) {
            log.error("Error preparing transaction data for persistence: transactionId={}",
                    request.getTransactionId(), e);
            // 不影响主流程
        }
    }

    /**
     * 从请求中提取商户ID
     */
    private String extractMerchantId(OrchestratorRequest request) {
        if (request.getAttributes() != null && request.getAttributes().containsKey("merchant_id")) {
            Object merchantId = request.getAttributes().get("merchant_id");
            if (merchantId != null) {
                return merchantId.toString();
            }
        }
        return "DEFAULT_MERCHANT";
    }
}
