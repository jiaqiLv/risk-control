package com.risk.orch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Orchestrator service configuration properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    /**
     * Feature service URL
     */
    private String featureServiceUrl = "http://localhost:8082";

    /**
     * Decision service URL
     */
    private String decisionServiceUrl = "http://localhost:8083";

    /**
     * Python inference service (gRPC)
     */
    private String pythonInferenceHost = "localhost";
    private int pythonInferencePort = 50051;

    /**
     * Timeout settings (milliseconds)
     */
    private int featureServiceTimeoutMs = 2000;
    private int decisionServiceTimeoutMs = 1000;
    private int pythonInferenceTimeoutMs = 3000;

    /**
     * Orchestrator mode: HYBRID, RULES_ONLY, MODEL_ONLY
     */
    private Mode mode = Mode.HYBRID;

    /**
     * Enable fallback to rules when model fails
     */
    private boolean enableFallback = true;

    /**
     * Risk score threshold for REVIEW decision
     */
    private double reviewThreshold = 0.5;

    /**
     * Risk score threshold for REJECT decision
     */
    private double rejectThreshold = 0.7;

    /**
     * Enable request/response logging
     */
    private boolean logRequests = true;
    private boolean logResponses = true;

    /**
     * Enable mock mode (for standalone testing)
     */
    private boolean mockMode = false;

    /**
     * Enable async persistence (event-driven)
     * true: 异步持久化，不阻塞主流程
     * false: 同步持久化，立即写入数据库
     */
    private boolean asyncPersistence = true;

    public enum Mode {
        HYBRID,      // Use both rules and model
        RULES_ONLY,  // Use only decision rules
        MODEL_ONLY   // Use only Python model
    }
}
