package com.risk.txn.dto;

import com.risk.data.entity.TransactionEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String transactionId;
    private String userId;
    private Long eventTimestamp;
    private Double amount;
    private String currency;
    private String productCd;
    private String channel;
    private String merchantId;
    private String decision;
    private Double riskScore;
    private Boolean isFraud;
    private Long processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> attributes;

    /**
     * 从实体转换为响应 DTO
     */
    public static TransactionResponse fromEntity(TransactionEntity entity) {
        return TransactionResponse.builder()
                .transactionId(entity.getTransactionId())
                .userId(entity.getUserId())
                .eventTimestamp(entity.getEventTimestamp())
                .amount(entity.getAmount() != null ? entity.getAmount().doubleValue() : null)
                .currency("USD") // 默认货币
                .productCd(entity.getProductCd())
                .merchantId(entity.getMerchantId())
                .decision(entity.getDecision() != null ? entity.getDecision().name() : null)
                .riskScore(entity.getRiskScore())
                .isFraud(entity.getIsFraud())
                .processedAt(entity.getUpdatedAt() != null ?
                        entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                        entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
