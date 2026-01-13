package com.risk.txn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 时间序列数据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesData {

    private String userId;
    private String window;
    private String metric;
    private List<DataPoint> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private Long timestamp;
        private Double value;
    }
}
