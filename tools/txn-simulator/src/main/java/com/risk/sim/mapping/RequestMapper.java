package com.risk.sim.mapping;

import com.risk.contracts.build.*;
import com.risk.sim.config.SimulatorProperties;
import com.risk.sim.source.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps TransactionRecord to various request formats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestMapper {

    private final SimulatorProperties properties;

    /**
     * Map a TransactionRecord to a gRPC InferenceRequest.
     *
     * @param record Transaction record from IEEE-CIS dataset
     * @return gRPC InferenceRequest
     */
    public InferenceRequest mapToGrpcRequest(TransactionRecord record) {
        String requestId = "sim-" + record.getTransactionId();

        InferenceRequest.Builder requestBuilder = InferenceRequest.newBuilder()
                .setRequestId(requestId)
                .setRequestTimestampMs(System.currentTimeMillis())
                .setModelName("fraud_detection_model")
                .setModelVersion("1.0.0")
                .setFeatureVersion("2026-01-09");

        // Map transaction context
        TransactionContext txContext = mapTransactionContext(record);
        requestBuilder.setTx(txContext);

        // Set inference options
        InferenceOptions options = mapInferenceOptions();
        requestBuilder.setOptions(options);

        // Note: subgraph, graph, and embeddings would be populated by the actual services
        // in production. For simulation, we're sending minimal data.

        return requestBuilder.build();
    }

    /**
     * Map a TransactionRecord to an HTTP request body (JSON).
     *
     * @param record Transaction record from IEEE-CIS dataset
     * @return HTTP request body as Map
     */
    public Map<String, Object> mapToHttpRequest(TransactionRecord record) {
        Map<String, Object> requestBody = new HashMap<>();

        // Basic transaction info
        requestBody.put("transactionId", record.getTransactionId());
        requestBody.put("userId", generateUserId(record));
        requestBody.put("eventTimestamp", record.getTransactionDt() != null
                ? record.getTransactionDt() * 1000 : System.currentTimeMillis());

        // Amount
        requestBody.put("transactionAmt", record.getTransactionAmt() != null
                ? record.getTransactionAmt() : 0.0);

        // Product and channel
        requestBody.put("productCd", record.getProductCd() != null
                ? record.getProductCd().toString() : "W");

        // Additional attributes
        Map<String, Object> attributes = new HashMap<>();

        // Card information
        if (record.getCard1() != null) attributes.put("card1", record.getCard1());
        if (record.getCard2() != null) attributes.put("card2", record.getCard2());
        if (record.getCard3() != null) attributes.put("card3", record.getCard3());
        if (record.getCard4() != null) attributes.put("card4", record.getCard4());
        if (record.getCard5() != null) attributes.put("card5", record.getCard5());
        if (record.getCard6() != null) attributes.put("card6", record.getCard6());

        // Address information
        if (record.getAddr1() != null) attributes.put("addr1", record.getAddr1());
        if (record.getAddr2() != null) attributes.put("addr2", record.getAddr2());

        // Distance
        if (record.getDist1() != null) attributes.put("dist1", record.getDist1());
        if (record.getDist2() != null) attributes.put("dist2", record.getDist2());

        // Device info
        if (record.getDeviceInfo() != null) {
            attributes.put("deviceInfo", record.getDeviceInfo());
        }
        if (record.getDeviceType() != null) {
            attributes.put("deviceType", record.getDeviceType());
        }

        // Email domain (from id-01)
        if (record.getId01() != null) {
            attributes.put("emailDomain", record.getId01());
        }

        // Device info (from id-31 to id-38)
        if (record.getId31() != null) {
            attributes.put("id31", record.getId31());
        }
        if (record.getId32() != null) {
            attributes.put("id32", record.getId32());
        }

        requestBody.put("attributes", attributes);

        return requestBody;
    }

    private TransactionContext mapTransactionContext(TransactionRecord record) {
        TransactionContext.Builder builder = TransactionContext.newBuilder()
                .setTransactionId(record.getTransactionId())
                .setUserId(generateUserId(record))
                .setEventTimestampMs(record.getTransactionDt() != null
                        ? record.getTransactionDt() * 1000 : System.currentTimeMillis())
                .setAmount(record.getTransactionAmt() != null
                        ? record.getTransactionAmt().doubleValue() : 0.0)
                .setCurrency("USD");

        // ProductCD
        if (record.getProductCd() != null) {
            builder.setProductCd(record.getProductCd().toString());
        } else {
            builder.setProductCd("W"); // Default
        }

        // Add additional attributes
        Map<String, FeatureValue> attributes = new HashMap<>();

        // Card fields
        putFeatureValue(attributes, "card1", record.getCard1());
        putFeatureValue(attributes, "card2", record.getCard2());
        putFeatureValue(attributes, "card3", record.getCard3());
        putFeatureValue(attributes, "card4", record.getCard4());
        putFeatureValue(attributes, "card5", record.getCard5());
        putFeatureValue(attributes, "card6", record.getCard6());

        // Address fields
        putFeatureValue(attributes, "addr1", record.getAddr1());
        putFeatureValue(attributes, "addr2", record.getAddr2());

        // Distance fields
        putFeatureValue(attributes, "dist1", record.getDist1());
        putFeatureValue(attributes, "dist2", record.getDist2());

        // Device info
        if (record.getDeviceInfo() != null) {
            attributes.put("deviceInfo", FeatureValue.newBuilder()
                    .setStr(record.getDeviceInfo())
                    .build());
        }

        // Email domain
        if (record.getId01() != null) {
            attributes.put("emailDomain", FeatureValue.newBuilder()
                    .setStr(record.getId01())
                    .build());
        }

        builder.putAllAttributes(attributes);

        return builder.build();
    }

    private InferenceOptions mapInferenceOptions() {
        InferenceOptions.Builder builder = InferenceOptions.newBuilder();

        // Set missing embedding policy based on configuration
        builder.setMissingEmbeddingPolicy(InferenceOptions.MissingEmbeddingPolicy.MODEL_DEFAULT);

        // Require user node (for cold start testing)
        if (properties.getColdStart().isEnabled()) {
            builder.setRequireUserNode(true);
        }

        // Allow Python to fetch if needed
        builder.setAllowPythonFetch(true);

        return builder.build();
    }

    // private void putFeatureValue(Map<String, FeatureValue> attributes, String key, Integer value) {
    //     if (value != null) {
    //         attributes.put(key, FeatureValue.newBuilder()
    //                 .setI64(value.longValue())
    //                 .build());
    //     }
    // }

    private void putFeatureValue(Map<String, FeatureValue> attributes, String key, Double value) {
        if (value != null) {
            attributes.put(key, FeatureValue.newBuilder()
                    .setF64(value.doubleValue())
                    .build());
        }
    }

    private void putFeatureValue(Map<String, FeatureValue> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, FeatureValue.newBuilder()
                    .setStr(value)
                    .build());
        }
    }

    /**
     * Generate a user ID from transaction record.
     * Uses card and address fields to create a unique identifier.
     */
    public String generateUserId(TransactionRecord record) {
        StringBuilder userId = new StringBuilder();

        if (record.getCard1() != null) {
            userId.append("card1_").append(record.getCard1());
        } else if (record.getAddr1() != null) {
            userId.append("addr1_").append(record.getAddr1());
        } else {
            userId.append("txn_").append(record.getTransactionId());
        }

        return userId.toString();
    }

    /**
     * Generate a synthetic user ID for cold start simulation.
     *
     * @param originalUserId Original user ID
     * @return New synthetic user ID
     */
    public String generateColdStartUserId(String originalUserId) {
        return "COLD_START_" + originalUserId + "_" + System.currentTimeMillis();
    }
}
