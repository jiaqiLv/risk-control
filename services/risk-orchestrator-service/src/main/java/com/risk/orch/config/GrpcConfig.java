package com.risk.orch.config;

import com.risk.contracts.build.RiskInfraServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * gRPC client configuration for Python inference service.
 */
@Slf4j
@Configuration
public class GrpcConfig {

    @Autowired
    private OrchestratorProperties properties;

    private ManagedChannel channel;

    @Bean
    public ManagedChannel grpcChannel() {
        this.channel = ManagedChannelBuilder
                .forAddress(properties.getPythonInferenceHost(), properties.getPythonInferencePort())
                .usePlaintext()
                .build();

        log.info("gRPC channel created: {}:{}", properties.getPythonInferenceHost(), properties.getPythonInferencePort());

        return channel;
    }

    @Bean
    public RiskInfraServiceGrpc.RiskInfraServiceBlockingStub grpcStub(ManagedChannel grpcChannel) {
        return RiskInfraServiceGrpc.newBlockingStub(grpcChannel);
    }

    @PreDestroy
    public void destroy() {
        if (channel != null && !channel.isShutdown()) {
            log.info("Shutting down gRPC channel");
            channel.shutdown();
        }
    }
}
