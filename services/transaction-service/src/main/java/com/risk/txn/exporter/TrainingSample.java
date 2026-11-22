package com.risk.txn.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Training data sample for machine learning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSample {

    @JsonProperty("label")
    private int label; // 0 = not fraud, 1 = fraud

    @JsonProperty("features")
    private Map<String, Object> features;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("riskScore")
    private Double riskScore;
}
