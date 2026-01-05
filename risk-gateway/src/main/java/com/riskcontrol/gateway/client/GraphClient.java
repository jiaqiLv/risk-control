package com.riskcontrol.gateway.client;

import com.riskcontrol.common.error.BusinessException;
import com.riskcontrol.common.error.ErrorCode;
import fraud.graph.v1.SampleSubgraphRequest;
import fraud.graph.v1.SampleSubgraphResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GraphClient {

    @GrpcClient("graph-service")
    private fraud.graph.v1.GraphServiceGrpc.GraphServiceBlockingStub graphServiceBlockingStub;

    public Mono<SampleSubgraphResponse> sampleSubgraph(SampleSubgraphRequest request) {
        return Mono.fromCallable(() -> {
            try {
                return graphServiceBlockingStub.sampleSubgraph(request);
            } catch (StatusRuntimeException e) {
                throw new BusinessException(ErrorCode.GRAPH_SERVICE_ERROR, "Graph service error: " + e.getStatus(), e);
            }
        });
    }
}
