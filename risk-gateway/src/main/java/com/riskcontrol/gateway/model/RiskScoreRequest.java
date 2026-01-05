package com.riskcontrol.gateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RiskScoreRequest {

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Subject type is required")
    private String subjectType; // user, device, merchant, etc.

    @NotBlank(message = "Subject ID is required")
    private String subjectId;

    @NotNull(message = "Transaction amount is required")
    private Double transactionAmount;

    private String currency;

    private String merchantId;

    private String deviceId;

    private String ipAddress;

    private Map<String, String> metadata;

    private String modelVersion;

    private Long timestampMs;
}
