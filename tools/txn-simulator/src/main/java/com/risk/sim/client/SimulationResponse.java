package com.risk.sim.client;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Response from a simulated transaction request.
 */
@Data
@Builder
public class SimulationResponse {

    private boolean success;
    private long latencyMs;
    private int statusCode;
    private Map<String, Object> responseBody;
    private String errorMessage;

    /**
     * Get the risk score from the response.
     *
     * @return Risk score, or null if not available
     */
    public Double getRiskScore() {
        if (responseBody == null) {
            return null;
        }

        Object scoreObj = responseBody.get("riskScore");
        if (scoreObj instanceof Number) {
            return ((Number) scoreObj).doubleValue();
        }

        return null;
    }

    /**
     * Get the decision from the response.
     *
     * @return Decision string, or null if not available
     */
    public String getDecision() {
        if (responseBody == null) {
            return null;
        }

        Object decisionObj = responseBody.get("decision");
        return decisionObj != null ? decisionObj.toString() : null;
    }

    /**
     * Check if the transaction was approved.
     *
     * @return true if approved, false otherwise
     */
    public boolean isApproved() {
        return "APPROVE".equalsIgnoreCase(getDecision()) || "ACCEPT".equalsIgnoreCase(getDecision());
    }

    /**
     * Check if the transaction was rejected.
     *
     * @return true if rejected, false otherwise
     */
    public boolean isRejected() {
        return "REJECT".equalsIgnoreCase(getDecision()) || "DECLINE".equalsIgnoreCase(getDecision());
    }

    /**
     * Check if the transaction needs review.
     *
     * @return true if needs review, false otherwise
     */
    public boolean isReview() {
        return "REVIEW".equalsIgnoreCase(getDecision());
    }
}
