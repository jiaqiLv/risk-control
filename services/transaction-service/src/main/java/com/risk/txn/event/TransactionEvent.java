package com.risk.txn.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Event representing a transaction for async data collection.
 * This event is consumed from Kafka for persistence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("eventType")
    private String eventType; // TRANSACTION_CREATED

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("payload")
    private TransactionPayload payload;

    @JsonProperty("metadata")
    private EventMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionPayload {
        @JsonProperty("transactionId")
        private String transactionId;

        @JsonProperty("userId")
        private String userId;

        @JsonProperty("merchantId")
        private String merchantId;

        @JsonProperty("eventTimestamp")
        private Long eventTimestamp;

        @JsonProperty("amount")
        private Double amount;

        @JsonProperty("productCd")
        private String productCd;

        @JsonProperty("decision")
        private String decision;

        @JsonProperty("riskScore")
        private Double riskScore;

        @JsonProperty("isFraud")
        private Boolean isFraud;

        @JsonProperty("attributes")
        private Map<String, Object> attributes;

        @JsonProperty("identity")
        private Map<String, Object> identity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventMetadata {
        @JsonProperty("gatewayTimestamp")
        private Long gatewayTimestamp;

        @JsonProperty("responseTime")
        private Long responseTime;

        @JsonProperty("sourceIp")
        private String sourceIp;

        @JsonProperty("userAgent")
        private String userAgent;
    }
}
