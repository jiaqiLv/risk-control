package com.risk.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Transaction response model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("decision")
    private String decision; // APPROVE, REVIEW, REJECT

    @JsonProperty("riskScore")
    private Double riskScore;

    @JsonProperty("reasons")
    private List<String> reasons;

    @JsonProperty("processedAt")
    private Long processedAt;

    @JsonProperty("statusCode")
    private Integer statusCode;

    @JsonProperty("message")
    private String message;
}
