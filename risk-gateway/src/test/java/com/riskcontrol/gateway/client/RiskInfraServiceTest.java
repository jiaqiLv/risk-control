package com.riskcontrol.gateway.client;

import com.riskcontrol.riskinfra.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for RiskInfraService gRPC client.
 * This test connects to a Python gRPC server running at the specified host and port.
 */
public class RiskInfraServiceTest {

    private ManagedChannel channel;
    private RiskInfraServiceGrpc.RiskInfraServiceBlockingStub blockingStub;

    // Update these values according to your Python server configuration
    private static final String HOST = "10.60.38.173";
    private static final int PORT = 49094;

    @BeforeEach
    public void setUp() {
        // Create a gRPC channel to connect to the Python server
        channel = ManagedChannelBuilder.forAddress(HOST, PORT)
                .usePlaintext()  // Use plaintext for testing (no TLS)
                .build();

        // Create a blocking stub
        blockingStub = RiskInfraServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void tearDown() {
        // Shutdown the channel when tests are done
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    public void testInferenceWithSampleData() {
        // Build a sample inference request
        InferenceRequest request = InferenceRequest.newBuilder()
                .setRequestId("test-request-001")
                .setRequestTimestampMs(System.currentTimeMillis())
                .setModelName("fraud_detection_model")
                .setModelVersion("v1.0.0")
                .setFeatureVersion("v1.0")
                .setSubgraph(buildSampleSubGraph())
                .build();

        System.out.println("Sending inference request:");
        System.out.println("  Request ID: " + request.getRequestId());
        System.out.println("  Model: " + request.getModelName() + " @ " + request.getModelVersion());
        System.out.println("  Nodes: " + request.getSubgraph().getNodesCount());
        System.out.println("  Edges: " + request.getSubgraph().getEdgesCount());

        try {
            // Call the gRPC service
            InferenceResponse response = blockingStub.inference(request);

            System.out.println("\nReceived inference response:");
            System.out.println("  Request ID: " + response.getRequestId());
            System.out.println("  Decision: " + response.getDecision());
            System.out.println("  Model: " + response.getModelName() + " @ " + response.getModelVersion());
            System.out.println("  Details: " + response.getDetailsMap());

            // Verify the response
            assertNotNull(response);
            assertEquals(request.getRequestId(), response.getRequestId());
            assertNotNull(response.getDecision());
            assertTrue(response.getDecision() == InferenceResponse.Decision.APPROVE
                    || response.getDecision() == InferenceResponse.Decision.REVIEW
                    || response.getDecision() == InferenceResponse.Decision.REJECT);

        } catch (io.grpc.StatusRuntimeException e) {
            System.err.println("gRPC error: " + e.getStatus());
            fail("Failed to call inference service: " + e.getStatus());
        }
    }

    @Test
    public void testInferenceWithComplexGraph() {
        // Build a more complex graph structure
        InferenceRequest request = InferenceRequest.newBuilder()
                .setRequestId("test-request-002")
                .setRequestTimestampMs(System.currentTimeMillis())
                .setModelName("graph_fraud_model")
                .setModelVersion("v2.0.0")
                .setFeatureVersion("v2.0")
                .setSubgraph(buildComplexSubGraph())
                .build();

        System.out.println("Testing with complex graph structure...");

        try {
            InferenceResponse response = blockingStub.inference(request);

            assertNotNull(response);
            assertEquals(request.getRequestId(), response.getRequestId());

            System.out.println("Complex graph test passed. Decision: " + response.getDecision());

        } catch (io.grpc.StatusRuntimeException e) {
            System.err.println("gRPC error: " + e.getStatus());
            fail("Failed to call inference service: " + e.getStatus());
        }
    }

    /**
     * Build a simple subgraph for testing
     */
    private SubGraph buildSampleSubGraph() {
        // Create nodes
        Node node1 = Node.newBuilder()
                .setNodeId("user_001")
                .setNodeType("user")
                .putFeatures("age", 35.0f)
                .putFeatures("account_age_days", 365.0f)
                .putFeatures("transaction_count", 50.0f)
                .build();

        Node node2 = Node.newBuilder()
                .setNodeId("device_001")
                .setNodeType("device")
                .putFeatures("risk_score", 0.3f)
                .putFeatures("is_rooted", 0.0f)
                .build();

        Node node3 = Node.newBuilder()
                .setNodeId("merchant_001")
                .setNodeType("merchant")
                .putFeatures("risk_score", 0.1f)
                .putFeatures("category_risk", 0.2f)
                .build();

        // Create edges
        Edge edge1 = Edge.newBuilder()
                .setEdgeId("edge_001")
                .setSourceNodeId("user_001")
                .setTargetNodeId("device_001")
                .setEdgeType("uses")
                .setWeight(1.0f)
                .putFeatures("frequency", 100.0f)
                .build();

        Edge edge2 = Edge.newBuilder()
                .setEdgeId("edge_002")
                .setSourceNodeId("user_001")
                .setTargetNodeId("merchant_001")
                .setEdgeType("transacts_with")
                .setWeight(0.8f)
                .putFeatures("transaction_amount", 150.0f)
                .build();

        // Build subgraph
        return SubGraph.newBuilder()
                .addNodes(node1)
                .addNodes(node2)
                .addNodes(node3)
                .addEdges(edge1)
                .addEdges(edge2)
                .setHops(2)
                .build();
    }

    /**
     * Build a more complex subgraph for testing
     */
    private SubGraph buildComplexSubGraph() {
        SubGraph.Builder builder = SubGraph.newBuilder();

        // Add multiple nodes
        for (int i = 0; i < 5; i++) {
            Node node = Node.newBuilder()
                    .setNodeId("node_" + i)
                    .setNodeType(i % 2 == 0 ? "user" : "device")
                    .putFeatures("feature_1", (float) Math.random())
                    .putFeatures("feature_2", (float) Math.random() * 100)
                    .build();
            builder.addNodes(node);
        }

        // Add multiple edges
        for (int i = 0; i < 4; i++) {
            Edge edge = Edge.newBuilder()
                    .setEdgeId("edge_" + i)
                    .setSourceNodeId("node_" + i)
                    .setTargetNodeId("node_" + (i + 1))
                    .setEdgeType("connects")
                    .setWeight((float) Math.random())
                    .putFeatures("weight", (float) Math.random())
                    .build();
            builder.addEdges(edge);
        }

        builder.setHops(3);
        return builder.build();
    }

    /**
     * Test connection to the gRPC server
     */
    @Test
    public void testConnection() {
        System.out.println("Testing connection to " + HOST + ":" + PORT);

        // Try to call the service with a minimal request
        InferenceRequest request = InferenceRequest.newBuilder()
                .setRequestId("ping")
                .setRequestTimestampMs(System.currentTimeMillis())
                .setModelName("test")
                .setModelVersion("v1.0")
                .setFeatureVersion("v1.0")
                .setSubgraph(SubGraph.newBuilder().build())
                .build();

        try {
            InferenceResponse response = blockingStub.inference(request);
            System.out.println("Connection successful! Response: " + response.getDecision());
            assertNotNull(response);
        } catch (io.grpc.StatusRuntimeException e) {
            System.err.println("Connection failed: " + e.getStatus());
            fail("Could not connect to gRPC server: " + e.getStatus());
        }
    }
}
