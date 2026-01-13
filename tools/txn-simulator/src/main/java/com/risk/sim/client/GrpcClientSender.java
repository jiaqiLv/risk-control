package com.risk.sim.client;

import com.risk.contracts.build.InferenceRequest;
import com.risk.contracts.build.InferenceResponse;
import com.risk.contracts.build.RiskInfraServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client for sending inference requests to the Python inference service.
 */
@Slf4j
@Component
public class GrpcClientSender {

    private ManagedChannel channel;
    private RiskInfraServiceGrpc.RiskInfraServiceBlockingStub blockingStub;
    private int timeoutMs;

    /**
     * Initialize the gRPC client with configuration.
     *
     * @param host      Target host
     * @param port      Target port
     * @param timeoutMs Request timeout in milliseconds
     */
    public void initialize(String host, int port, int timeoutMs) {
        this.timeoutMs = timeoutMs;

        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = RiskInfraServiceGrpc.newBlockingStub(channel);

        log.info("Initialized gRPC client: {}:{}, timeout={}ms", host, port, timeoutMs);
    }

    /**
     * Send an inference request.
     *
     * @param request gRPC InferenceRequest
     * @return SimulationResponse
     * @throws Exception if request fails
     */
    public SimulationResponse send(InferenceRequest request) throws Exception {
        if (blockingStub == null) {
            throw new IllegalStateException("gRPC client not initialized. Call initialize() first.");
        }

        long startTime = System.currentTimeMillis();

        try {
            InferenceResponse response = blockingStub
                    .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .inference(request);

            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            return SimulationResponse.builder()
                    .success(true)
                    .latencyMs(latency)
                    .statusCode(200)
                    .responseBody(convertResponseToMap(response))
                    .errorMessage(null)
                    .build();

        } catch (io.grpc.StatusRuntimeException e) {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            return SimulationResponse.builder()
                    .success(false)
                    .latencyMs(latency)
                    .statusCode(e.getStatus().getCode().value())
                    .responseBody(null)
                    .errorMessage("gRPC error: " + e.getStatus())
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

    private java.util.Map<String, Object> convertResponseToMap(InferenceResponse response) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();

        map.put("requestId", response.getRequestId());
        map.put("responseTimestampMs", response.getResponseTimestampMs());
        map.put("modelName", response.getModelName());
        map.put("modelVersion", response.getModelVersion());
        map.put("featureVersion", response.getFeatureVersion());
        map.put("decision", response.getDecision().name());
        map.put("riskScore", response.getRiskScore());
        map.put("topReasons", response.getTopReasonsList());

        // Convert meta
        java.util.Map<String, Object> metaMap = new java.util.HashMap<>();
        metaMap.put("fallbacksUsed", response.getMeta().getFallbacksUsedList());
        metaMap.put("missingInputs", response.getMeta().getMissingInputsList());
        metaMap.put("debug", response.getMeta().getDebugMap());
        map.put("meta", metaMap);

        return map;
    }

    /**
     * Shutdown the gRPC channel.
     */
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Failed to shutdown gRPC channel gracefully", e);
            }
        }
    }

    /**
     * Health check for the gRPC service.
     *
     * @return true if service is healthy, false otherwise
     */
    public boolean healthCheck() {
        // gRPC doesn't have a standard health check like HTTP
        // You would need to implement a custom health check RPC
        return blockingStub != null && !channel.isShutdown();
    }
}
