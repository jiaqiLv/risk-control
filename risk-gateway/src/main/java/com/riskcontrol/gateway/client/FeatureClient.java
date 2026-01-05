package com.riskcontrol.gateway.client;

import com.riskcontrol.common.error.BusinessException;
import com.riskcontrol.common.error.ErrorCode;
import fraud.feature.v1.GetFeaturesRequest;
import fraud.feature.v1.GetFeaturesResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FeatureClient {

    @GrpcClient("feature-service")
    private fraud.feature.v1.FeatureServiceGrpc.FeatureServiceBlockingStub featureServiceBlockingStub;

    public Mono<GetFeaturesResponse> getFeatures(GetFeaturesRequest request) {
        return Mono.fromCallable(() -> {
            try {
                return featureServiceBlockingStub.getFeatures(request);
            } catch (StatusRuntimeException e) {
                throw new BusinessException(ErrorCode.FEATURE_SERVICE_ERROR,
                        "Feature service error: " + e.getStatus(), e);
            }
        });
    }
}
