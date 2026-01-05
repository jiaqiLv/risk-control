package com.riskcontrol.feature.service;

import com.riskcontrol.feature.repository.FeatureRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GrpcService
public class FeatureGrpcService extends fraud.feature.v1.FeatureServiceGrpc.FeatureServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FeatureGrpcService.class);

    private final FeatureRepository featureRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public FeatureGrpcService(
            FeatureRepository featureRepository,
            @Qualifier("reactiveRedisTemplate")
            ReactiveRedisTemplate<String, String> redisTemplate) {
        this.featureRepository = featureRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void getFeatures(fraud.feature.v1.GetFeaturesRequest request,
            StreamObserver<fraud.feature.v1.GetFeaturesResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        String requestId = request.getRequestId();

        try {
            String featureVersion = request.getFeatureVersion().isEmpty() ? "v1" : request.getFeatureVersion();
            String cacheKey = buildFeatureKey(featureVersion, request.getSubjectType(), request.getSubjectId());

            // Try to get from Redis cache
            featureRepository.getFeatures(cacheKey)
                    .map(featureMap -> buildGetFeaturesResponse(requestId, featureMap, true, startTime))
                    .switchIfEmpty(Mono.defer(() -> {
                        // Cache miss - compute features online (simplified)
                        log.debug("Cache miss for key: {}, computing online features", cacheKey);
                        Map<String, String> computedFeatures = computeOnlineFeatures(request);
                        return Mono.just(buildGetFeaturesResponse(requestId, computedFeatures, false, startTime));
                    }))
                    .doOnNext(response -> {
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                        log.debug("Served {} features in {}ms (cache_hit={})",
                                response.getFeaturesCount(), response.getTotalLatencyMs(), response.getCacheHit());
                    })
                    .doOnError(error -> {
                        log.error("Error getting features for request: {}", requestId, error);
                        fraud.feature.v1.GetFeaturesResponse errorResponse = fraud.feature.v1.GetFeaturesResponse
                                .newBuilder()
                                .setRequestId(requestId)
                                .setErrorMessage(error.getMessage())
                                .setTotalLatencyMs(System.currentTimeMillis() - startTime)
                                .build();
                        responseObserver.onNext(errorResponse);
                        responseObserver.onCompleted();
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Unexpected error in getFeatures", e);
            fraud.feature.v1.GetFeaturesResponse errorResponse = fraud.feature.v1.GetFeaturesResponse.newBuilder()
                    .setRequestId(requestId)
                    .setErrorMessage(e.getMessage())
                    .setTotalLatencyMs(System.currentTimeMillis() - startTime)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void batchGetFeatures(fraud.feature.v1.BatchGetFeaturesRequest request,
            StreamObserver<fraud.feature.v1.BatchGetFeaturesResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        String requestId = request.getRequestId();

        try {
            List<fraud.feature.v1.GetFeaturesResponse> responses = new ArrayList<>();

            for (fraud.feature.v1.FeatureQuery query : request.getQueriesList()) {
                fraud.feature.v1.GetFeaturesRequest singleRequest = fraud.feature.v1.GetFeaturesRequest.newBuilder()
                        .setRequestId(requestId)
                        .setSubjectType(query.getSubjectType())
                        .setSubjectId(query.getSubjectId())
                        .addAllFeatureNames(query.getFeatureNamesList())
                        .setFeatureVersion(request.getFeatureVersion())
                        .build();

                // Process synchronously for batch (can be optimized)
                String featureVersion = request.getFeatureVersion().isEmpty() ? "v1" : request.getFeatureVersion();
                String cacheKey = buildFeatureKey(featureVersion, query.getSubjectType(), query.getSubjectId());

                Map<String, String> features = featureRepository.getFeatures(cacheKey).block();
                if (features == null) {
                    features = computeOnlineFeatures(singleRequest);
                }

                fraud.feature.v1.GetFeaturesResponse singleResponse = buildGetFeaturesResponse(requestId, features,
                        features != null, startTime);
                responses.add(singleResponse);
            }

            fraud.feature.v1.BatchGetFeaturesResponse batchResponse = fraud.feature.v1.BatchGetFeaturesResponse
                    .newBuilder()
                    .setRequestId(requestId)
                    .addAllResponses(responses)
                    .setTotalLatencyMs(System.currentTimeMillis() - startTime)
                    .build();

            responseObserver.onNext(batchResponse);
            responseObserver.onCompleted();

            log.debug("Batch served {} feature sets in {}ms", responses.size(), batchResponse.getTotalLatencyMs());

        } catch (Exception e) {
            log.error("Error in batchGetFeatures", e);
            fraud.feature.v1.BatchGetFeaturesResponse errorResponse = fraud.feature.v1.BatchGetFeaturesResponse
                    .newBuilder()
                    .setRequestId(requestId)
                    .setErrorMessage(e.getMessage())
                    .setTotalLatencyMs(System.currentTimeMillis() - startTime)
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private String buildFeatureKey(String version, String subjectType, String subjectId) {
        return String.format("feat:%s:%s:%s", version, subjectType, subjectId);
    }

    private fraud.feature.v1.GetFeaturesResponse buildGetFeaturesResponse(
            String requestId, Map<String, String> features, boolean cacheHit, long startTime) {

        fraud.feature.v1.GetFeaturesResponse.Builder responseBuilder = fraud.feature.v1.GetFeaturesResponse.newBuilder()
                .setRequestId(requestId)
                .setCacheHit(cacheHit)
                .setTotalLatencyMs(System.currentTimeMillis() - startTime);

        if (features != null) {
            features.forEach((key, value) -> {
                fraud.feature.v1.FeatureValue.Builder featureBuilder = fraud.feature.v1.FeatureValue.newBuilder()
                        .setName(key)
                        .setLastUpdatedMs(System.currentTimeMillis());

                // Try to parse as different types
                try {
                    double doubleValue = Double.parseDouble(value);
                    featureBuilder.setDoubleValue(doubleValue);
                } catch (NumberFormatException e1) {
                    try {
                        long longValue = Long.parseLong(value);
                        featureBuilder.setIntValue(longValue);
                    } catch (NumberFormatException e2) {
                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                            featureBuilder.setBoolValue(Boolean.parseBoolean(value));
                        } else {
                            featureBuilder.setStringValue(value);
                        }
                    }
                }

                responseBuilder.addFeatures(featureBuilder.build());
            });
        }

        return responseBuilder.build();
    }

    private Map<String, String> computeOnlineFeatures(fraud.feature.v1.GetFeaturesRequest request) {
        // Simplified online feature computation
        // In production, this would call complex feature engineering logic
        return Map.of(
                "transaction_count_24h", "5",
                "avg_transaction_amount_7d", "150.50",
                "account_age_days", "365",
                "is_new_device", "false",
                "risk_score_30d", "0.15");
    }
}
