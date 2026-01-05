package com.riskcontrol.gateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RiskScoreResponse {

    private String requestId;
    private String eventId;
    private String subjectType;
    private String subjectId;
    private double fraudScore;
    private String decision; // ALLOW, REVIEW, REJECT
    private String reason;
    private String modelVersion;
    private Long processingTimeMs;
    private Boolean cached;
}
