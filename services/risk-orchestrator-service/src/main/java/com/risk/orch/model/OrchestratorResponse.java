package com.risk.orch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Orchestrator response model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestratorResponse {

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

    @JsonProperty("modelUsed")
    private String modelUsed;

    @JsonProperty("rulesTriggered")
    private List<String> rulesTriggered;

    @JsonProperty("fallbackUsed")
    private String fallbackUsed;

    @JsonProperty("debugInfo")
    private String debugInfo;
}
