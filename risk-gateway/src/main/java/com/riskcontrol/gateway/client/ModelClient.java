package com.riskcontrol.gateway.client;

import com.riskcontrol.common.error.BusinessException;
import com.riskcontrol.common.error.ErrorCode;
import fraud.model.v1.PredictRequest;
import fraud.model.v1.PredictResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ModelClient {

    @GrpcClient("model-service")
    private fraud.model.v1.ModelServingGrpc.ModelServingBlockingStub modelServingBlockingStub;

    public Mono<PredictResponse> predict(PredictRequest request) {
        return Mono.fromCallable(() -> {
            try {
                return modelServingBlockingStub.predict(request);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                    throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR,
                            "Model service unavailable", e);
                }
                throw new BusinessException(ErrorCode.MODEL_SERVICE_ERROR,
                        "Model service error: " + e.getStatus(), e);
            }
        });
    }
}
