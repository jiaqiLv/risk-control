package com.risk.txn.controller;

import com.risk.txn.dto.*;
import com.risk.txn.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Transaction Controller
 *
 * 交易服务 REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * 创建交易记录
     * POST /api/v1/transactions
     */
    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody TransactionCreateRequest request) {

        log.info("Received create transaction request: transactionId={}, userId={}",
                request.getTransactionId(), request.getUserId());

        try {
            TransactionResponse response = transactionService.createTransaction(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("Failed to create transaction: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating transaction", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to create transaction"));
        }
    }

    /**
     * 查询交易详情
     * GET /api/v1/transactions/{transactionId}
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable String transactionId) {

        log.debug("Received get transaction request: transactionId={}", transactionId);

        try {
            TransactionResponse response = transactionService.getTransactionById(transactionId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.warn("Transaction not found: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
    }

    /**
     * 查询用户历史交易
     * GET /api/v1/users/{userId}/history
     */
    @GetMapping("/users/{userId}/history")
    public ResponseEntity<ApiResponse<UserHistoryResponse>> getUserHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {

        log.info("Received user history request: userId={}, startTime={}, endTime={}, limit={}",
                userId, startTime, endTime, limit);

        try {
            UserHistoryResponse response = transactionService.getUserHistory(
                    userId, startTime, endTime, limit);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error fetching user history", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to fetch user history"));
        }
    }

    /**
     * 获取用户实时统计特征
     * GET /api/v1/users/{userId}/realtime-stats
     */
    @GetMapping("/users/{userId}/realtime-stats")
    public ResponseEntity<ApiResponse<UserStatsSummary>> getUserRealtimeStats(
            @PathVariable String userId) {

        log.debug("Received user realtime stats request: userId={}", userId);

        try {
            UserStatsSummary stats = transactionService.getUserRealtimeStats(userId);
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Error fetching user realtime stats", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to fetch realtime stats"));
        }
    }

    /**
     * 获取用户时间序列数据
     * GET /api/v1/users/{userId}/timeseries
     */
    @GetMapping("/users/{userId}/timeseries")
    public ResponseEntity<ApiResponse<TimeSeriesData>> getUserTimeSeries(
            @PathVariable String userId,
            @RequestParam(defaultValue = "24h") String window,
            @RequestParam(defaultValue = "count") String metric) {

        log.info("Received user time series request: userId={}, window={}, metric={}",
                userId, window, metric);

        try {
            TimeSeriesData data = transactionService.getUserTimeSeries(userId, window, metric);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameter: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_PARAMETER", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching time series data", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Failed to fetch time series"));
        }
    }

    /**
     * 健康检查
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        HealthStatus health = HealthStatus.builder()
                .status("UP")
                .service("transaction-service")
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    /**
     * 服务信息
     * GET /api/v1/info
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<ServiceInfo>> info() {
        ServiceInfo info = ServiceInfo.builder()
                .service("transaction-service")
                .version("1.0.0-SNAPSHOT")
                .description("Transaction Storage and Query Service")
                .build();

        return ResponseEntity.ok(ApiResponse.success(info));
    }

    /**
     * 健康状态响应
     */
    @lombok.Data
    @lombok.Builder
    private static class HealthStatus {
        private String status;
        private String service;
        private Long timestamp;
    }

    /**
     * 服务信息响应
     */
    @lombok.Data
    @lombok.Builder
    private static class ServiceInfo {
        private String service;
        private String version;
        private String description;
    }
}
