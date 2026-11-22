package com.riskcontrol.gateway.client;

import com.riskcontrol.common.error.BusinessException;
import com.riskcontrol.common.error.ErrorCode;
import com.riskcontrol.riskinfra.InferenceRequest;
import com.riskcontrol.riskinfra.InferenceResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RiskInfraClient {

    @GrpcClient("risk-infra-service")
    private com.riskcontrol.riskinfra.RiskInfraServiceGrpc.RiskInfraServiceBlockingStub riskInfraBlockingStub;

    public Mono<InferenceResponse> inference(InferenceRequest request) {
        return Mono.fromCallable(() -> {
            try {
                return riskInfraBlockingStub.inference(request);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                    throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR,
                            "Risk infra service unavailable", e);
                }
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR,
                        "Risk infra service error: " + e.getStatus(), e);
            }
        });
    }
}
