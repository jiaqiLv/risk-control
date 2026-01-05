package com.riskcontrol.graph.service;

import com.riskcontrol.graph.model.GraphEdge;
import com.riskcontrol.graph.model.GraphNode;
import com.riskcontrol.graph.repository.GraphRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GrpcService
public class GraphGrpcService extends fraud.graph.v1.GraphServiceGrpc.GraphServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(GraphGrpcService.class);

    private final GraphRepository graphRepository;

    public GraphGrpcService(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public void upsertNodes(fraud.graph.v1.UpsertNodesRequest request,
                           StreamObserver<fraud.graph.v1.UpsertNodesResponse> responseObserver) {
        try {
            for (fraud.graph.v1.Node protoNode : request.getNodesList()) {
                GraphNode node = GraphNode.fromProto(protoNode);
                graphRepository.upsertNode(node);
            }

            fraud.graph.v1.UpsertNodesResponse response =
                    fraud.graph.v1.UpsertNodesResponse.newBuilder()
                            .setNodesUpdated(request.getNodesCount())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.debug("Upserted {} nodes", request.getNodesCount());
        } catch (Exception e) {
            log.error("Error upserting nodes", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void upsertEdges(fraud.graph.v1.UpsertEdgesRequest request,
                           StreamObserver<fraud.graph.v1.UpsertEdgesResponse> responseObserver) {
        try {
            for (fraud.graph.v1.Edge protoEdge : request.getEdgesList()) {
                GraphEdge edge = GraphEdge.fromProto(protoEdge);
                graphRepository.upsertEdge(edge);
            }

            fraud.graph.v1.UpsertEdgesResponse response =
                    fraud.graph.v1.UpsertEdgesResponse.newBuilder()
                            .setEdgesUpdated(request.getEdgesCount())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.debug("Upserted {} edges", request.getEdgesCount());
        } catch (Exception e) {
            log.error("Error upserting edges", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void sampleSubgraph(fraud.graph.v1.SampleSubgraphRequest request,
                              StreamObserver<fraud.graph.v1.SampleSubgraphResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        try {
            Set<String> nodeIds = new HashSet<>(request.getSeedNodeIdsList());
            Set<String> visitedNodes = new HashSet<>();
            Set<String> visitedEdges = new HashSet<>();

            int hops = request.getHops() > 0 ? request.getHops() : 2;
            int neighborLimit = request.getNeighborLimit() > 0 ? request.getNeighborLimit() : 100;
            long startTimeMs = request.getStartTimeMs() > 0 ? request.getStartTimeMs() : 0;
            long endTimeMs = request.getEndTimeMs() > 0 ? request.getEndTimeMs() : System.currentTimeMillis();

            // BFS traversal
            Set<String> currentLevel = new HashSet<>(request.getSeedNodeIdsList());

            for (int i = 0; i < hops && !currentLevel.isEmpty(); i++) {
                Set<String> nextLevel = new HashSet<>();

                for (String nodeId : currentLevel) {
                    if (visitedNodes.contains(nodeId)) {
                        continue;
                    }

                    visitedNodes.add(nodeId);

                    // Get neighbors
                    List<GraphNode> neighbors = graphRepository.getNeighbors(
                            nodeId,
                            request.getEdgeTypesCount() > 0 ? request.getEdgeTypes(0) : "",
                            neighborLimit
                    );

                    for (GraphNode neighbor : neighbors) {
                        if (!visitedNodes.contains(neighbor.getNodeId())) {
                            nextLevel.add(neighbor.getNodeId());
                            nodeIds.add(neighbor.getNodeId());
                        }

                        // Get edges between current node and neighbor
                        List<GraphEdge> edgesBetween = graphRepository.getEdges(
                                nodeId,
                                neighbor.getNodeId(),
                                request.getEdgeTypesCount() > 0 ? request.getEdgeTypes(0) : "",
                                startTimeMs,
                                endTimeMs
                        );

                        for (GraphEdge edge : edgesBetween) {
                            visitedEdges.add(edge.getEdgeId());
                        }
                    }
                }

                currentLevel = nextLevel;
            }

            // Build response
            fraud.graph.v1.SampleSubgraphResponse.Builder responseBuilder =
                    fraud.graph.v1.SampleSubgraphResponse.newBuilder();

            for (String nodeId : nodeIds) {
                graphRepository.getNode(nodeId).ifPresent(node ->
                        responseBuilder.addNodes(node.toProto()));
            }

            for (String edgeId : visitedEdges) {
                graphRepository.getEdge(edgeId).ifPresent(edge ->
                        responseBuilder.addEdges(edge.toProto()));
            }

            long latency = System.currentTimeMillis() - startTime;

            responseBuilder
                    .setSnapshotTimeMs(System.currentTimeMillis())
                    .setLatencyMs(latency);

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            log.debug("Sampled subgraph: {} nodes, {} edges in {}ms",
                    nodeIds.size(), visitedEdges.size(), latency);
        } catch (Exception e) {
            log.error("Error sampling subgraph", e);
            fraud.graph.v1.SampleSubgraphResponse errorResponse =
                    fraud.graph.v1.SampleSubgraphResponse.newBuilder()
                            .setErrorMessage(e.getMessage())
                            .setLatencyMs(System.currentTimeMillis() - startTime)
                            .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
}
