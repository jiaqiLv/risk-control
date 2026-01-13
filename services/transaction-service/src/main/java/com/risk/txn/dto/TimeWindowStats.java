package com.risk.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间窗口统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeWindowStats {

    private WindowStats last24h;
    private WindowStats last7d;
    private WindowStats last30d;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WindowStats {
        private Long count;
        private Double totalAmount;
        private Double avgAmount;
    }
}
