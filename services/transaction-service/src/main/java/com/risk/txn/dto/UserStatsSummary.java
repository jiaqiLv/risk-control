package com.risk.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户统计摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsSummary {

    private Long totalTxns;
    private Double totalAmount;
    private Double avgAmount;
    private Long approvedTxns;
    private Long reviewedTxns;
    private Long rejectedTxns;
    private Double approvalRate;
}
