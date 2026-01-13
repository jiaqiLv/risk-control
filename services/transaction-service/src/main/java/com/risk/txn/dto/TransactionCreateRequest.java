package com.risk.txn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 交易创建请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCreateRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Event timestamp is required")
    private Long eventTimestamp;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Double amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String productCd;

    private String channel;

    private String merchantId;

    private String deviceId;

    private String ipAddress;

    private Map<String, Object> attributes;

    private Decision decision;

    private Double riskScore;

    private Boolean isFraud;

    /**
     * 决策类型枚举
     */
    public enum Decision {
        APPROVE,
        REVIEW,
        REJECT,
        PENDING
    }
}
