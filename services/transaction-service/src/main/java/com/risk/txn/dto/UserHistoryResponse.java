package com.risk.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户历史查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserHistoryResponse {

    private String userId;
    private UserStatsSummary summary;
    private TimeWindowStats timeWindowStats;
    private List<TransactionResponse> transactions;
}
