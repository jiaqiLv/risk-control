package com.risk.orch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Orchestrator request model (same as Gateway request).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestratorRequest {

    @NotBlank(message = "transactionId is required")
    @JsonProperty("transactionId")
    private String transactionId;

    @NotBlank(message = "userId is required")
    @JsonProperty("userId")
    private String userId;

    @NotNull(message = "eventTimestamp is required")
    @JsonProperty("eventTimestamp")
    private Long eventTimestamp;

    @NotNull(message = "amount is required")
    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("productCd")
    private String productCd;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("attributes")
    private Map<String, Object> attributes;
}
