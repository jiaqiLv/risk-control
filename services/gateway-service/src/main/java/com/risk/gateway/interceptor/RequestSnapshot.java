package com.risk.gateway.interceptor;

import com.risk.gateway.model.TransactionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Snapshot of transaction request for async data collection.
 * Stored in ThreadLocal during request processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestSnapshot {

    private String transactionId;
    private String userId;
    private Double amount;
    private String productCd;
    private Long requestTimestamp;
    private String sourceIp;
    private String userAgent;
    private Map<String, Object> requestData;
    private Map<String, Object> attributes;
    private Map<String, Object> identity;

    /**
     * Original TransactionRequest object
     */
    private TransactionRequest transactionRequest;
}
