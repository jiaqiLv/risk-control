package com.riskcontrol.common.error;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // Success
    SUCCESS(0, "Success"),

    // Client errors (4xxx)
    BAD_REQUEST(4000, "Bad request"),
    UNAUTHORIZED(4001, "Unauthorized"),
    FORBIDDEN(4003, "Forbidden"),
    NOT_FOUND(4004, "Resource not found"),
    DUPLICATE_REQUEST(4009, "Duplicate request"),

    // Server errors (5xxx)
    INTERNAL_ERROR(5000, "Internal server error"),
    SERVICE_UNAVAILABLE(5003, "Service unavailable"),
    TIMEOUT(5004, "Request timeout"),
    CIRCUIT_BREAKER_OPEN(5005, "Circuit breaker is open"),
    RATE_LIMIT_EXCEEDED(5006, "Rate limit exceeded"),

    // Business errors (6xxx)
    INVALID_SUBJECT_TYPE(6001, "Invalid subject type"),
    INVALID_FEATURE_VERSION(6002, "Invalid feature version"),
    THRESHOLD_NOT_FOUND(6003, "Threshold configuration not found"),
    MODEL_SERVICE_ERROR(6004, "Model service error"),
    GRAPH_SERVICE_ERROR(6005, "Graph service error"),
    FEATURE_SERVICE_ERROR(6006, "Feature service error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
