package com.risk.gateway.model;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rate limit rule model for Sentinel flow control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    /**
     * Resource name (usually the API endpoint path).
     * Example: /api/v1/transactions
     */
    @NotBlank(message = "Resource cannot be blank")
    private String resource;

    /**
     * Limit application (default: "default" for all).
     */
    @Builder.Default
    private String limitApp = "default";

    /**
     * Flow control grade (0: thread count, 1: QPS).
     * Default: 1 (QPS)
     */
    @Builder.Default
    @NotNull(message = "Grade cannot be null")
    private Integer grade = RuleConstant.FLOW_GRADE_QPS;

    /**
     * Threshold count.
     * For QPS: requests per second
     * For thread count: concurrent threads
     */
    @NotNull(message = "Count cannot be null")
    @Min(value = 1, message = "Count must be at least 1")
    private Long count;

    /**
     * Strategy (0: direct reject, 1: warmup, 2: rate limiter).
     * Default: 0 (direct reject)
     */
    @Builder.Default
    @Min(value = 0, message = "Strategy must be 0, 1, or 2")
    @Max(value = 2, message = "Strategy must be 0, 1, or 2")
    private Integer strategy = RuleConstant.STRATEGY_DIRECT;

    /**
     * Control behavior (0: reject, 1: warmup, 2: throttling).
     * Default: 0 (reject)
     */
    @Builder.Default
    @Min(value = 0, message = "Control behavior must be 0, 1, or 2")
    @Max(value = 2, message = "Control behavior must be 0, 1, or 2")
    private Integer controlBehavior = RuleConstant.CONTROL_BEHAVIOR_DEFAULT;

    /**
     * Warmup period in seconds (for warmup strategy).
     * Default: 10 seconds
     */
    @Builder.Default
    @Min(value = 1, message = "Warmup period must be at least 1 second")
    private Integer warmUpPeriodSec = 10;

    /**
     * Timeout in seconds (for rate limiter strategy).
     * Default: 0 (no timeout)
     */
    @Builder.Default
    @Min(value = 0, message = "Timeout must be non-negative")
    private Integer timeoutInSec = 0;

    /**
     * Cluster mode (true for cluster-wide rate limiting).
     * Default: false
     */
    @Builder.Default
    private Boolean clusterMode = false;

    /**
     * Rule description.
     */
    private String description;
}
