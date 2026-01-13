package com.risk.txn.service;

import com.risk.data.entity.Decision;
import com.risk.data.entity.TransactionEntity;
import com.risk.data.repository.TransactionRepository;
import com.risk.txn.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transaction Service
 *
 * 交易服务核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * 创建交易记录
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        log.info("Creating transaction: transactionId={}, userId={}, amount={}",
                request.getTransactionId(), request.getUserId(), request.getAmount());

        // 检查交易是否已存在
        transactionRepository.findByTransactionId(request.getTransactionId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Transaction already exists: " + request.getTransactionId());
                });

        // 构建实体
        TransactionEntity entity = TransactionEntity.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .eventTimestamp(request.getEventTimestamp())
                .amount(request.getAmount() != null ? java.math.BigDecimal.valueOf(request.getAmount()) : null)
                .productCd(request.getProductCd())
                .merchantId(request.getMerchantId())
                .decision(request.getDecision() != null ?
                        Decision.valueOf(request.getDecision().name()) : Decision.PENDING)
                .riskScore(request.getRiskScore())
                .isFraud(request.getIsFraud())
                .build();

        // 保存
        TransactionEntity saved = transactionRepository.save(entity);

        log.info("Transaction created successfully: transactionId={}, id={}",
                saved.getTransactionId(), saved.getId());

        return TransactionResponse.fromEntity(saved);
    }

    /**
     * 根据交易ID查询交易详情
     */
    public TransactionResponse getTransactionById(String transactionId) {
        log.debug("Fetching transaction: transactionId={}", transactionId);

        return transactionRepository.findByTransactionId(transactionId)
                .map(TransactionResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + transactionId));
    }

    /**
     * 查询用户历史交易
     */
    public UserHistoryResponse getUserHistory(String userId, Long startTime, Long endTime, Integer limit) {
        log.info("Fetching user history: userId={}, startTime={}, endTime={}, limit={}",
                userId, startTime, endTime, limit);

        // 查询交易列表
        List<TransactionEntity> transactions;
        if (startTime != null && endTime != null) {
            transactions = transactionRepository.findByUserIdAndEventTimestampBetween(
                    userId, startTime, endTime);
        } else {
            transactions = transactionRepository.findByUserIdOrderByEventTimestampDesc(userId);
        }

        // 限制返回数量
        if (limit != null && limit > 0 && transactions.size() > limit) {
            transactions = transactions.subList(0, limit);
        }

        // 转换为响应 DTO
        List<TransactionResponse> txnResponses = transactions.stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());

        // 构建统计摘要
        UserStatsSummary summary = calculateSummary(transactions);

        // 计算时间窗口统计
        TimeWindowStats timeWindowStats = calculateTimeWindowStats(userId);

        return UserHistoryResponse.builder()
                .userId(userId)
                .summary(summary)
                .timeWindowStats(timeWindowStats)
                .transactions(txnResponses)
                .build();
    }

    /**
     * 获取用户实时统计特征
     */
    public UserStatsSummary getUserRealtimeStats(String userId) {
        log.debug("Fetching user realtime stats: userId={}", userId);

        // 获取最近24小时的交易
        long now = System.currentTimeMillis();
        long oneDayAgo = now - (24 * 60 * 60 * 1000);

        List<TransactionEntity> recentTxns = transactionRepository
                .findByUserIdAndEventTimestampBetween(userId, oneDayAgo, now);

        return calculateSummary(recentTxns);
    }

    /**
     * 获取用户时间序列数据
     */
    public TimeSeriesData getUserTimeSeries(String userId, String window, String metric) {
        log.info("Fetching user time series: userId={}, window={}, metric={}",
                userId, window, metric);

        // 根据窗口大小计算时间范围
        long now = System.currentTimeMillis();
        long startTime;
        long intervalMillis;

        switch (window.toLowerCase()) {
            case "1h":
                startTime = now - (60 * 60 * 1000);
                intervalMillis = 5 * 60 * 1000; // 5分钟间隔
                break;
            case "24h":
                startTime = now - (24 * 60 * 60 * 1000);
                intervalMillis = 60 * 60 * 1000; // 1小时间隔
                break;
            case "7d":
                startTime = now - (7 * 24 * 60 * 60 * 1000);
                intervalMillis = 24 * 60 * 60 * 1000; // 1天间隔
                break;
            case "30d":
                startTime = now - (30 * 24 * 60 * 60 * 1000);
                intervalMillis = 24 * 60 * 60 * 1000; // 1天间隔
                break;
            default:
                throw new IllegalArgumentException("Invalid window: " + window);
        }

        // 查询交易数据
        List<TransactionEntity> transactions = transactionRepository
                .findByUserIdAndEventTimestampBetween(userId, startTime, now);

        // 简化版：直接返回交易点（后续可以优化为按时间桶聚合）
        List<TimeSeriesData.DataPoint> dataPoints = transactions.stream()
                .map(txn -> {
                    double value = switch (metric.toLowerCase()) {
                        case "count" -> 1.0;
                        case "amount" -> txn.getAmount() != null ? txn.getAmount().doubleValue() : 0.0;
                        case "avg_amount" -> txn.getAmount() != null ? txn.getAmount().doubleValue() : 0.0;
                        default -> throw new IllegalArgumentException("Invalid metric: " + metric);
                    };
                    return TimeSeriesData.DataPoint.builder()
                            .timestamp(txn.getEventTimestamp())
                            .value(value)
                            .build();
                })
                .collect(Collectors.toList());

        return TimeSeriesData.builder()
                .userId(userId)
                .window(window)
                .metric(metric)
                .data(dataPoints)
                .build();
    }

    /**
     * 计算用户统计摘要
     */
    private UserStatsSummary calculateSummary(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return UserStatsSummary.builder()
                    .totalTxns(0L)
                    .totalAmount(0.0)
                    .avgAmount(0.0)
                    .approvedTxns(0L)
                    .reviewedTxns(0L)
                    .rejectedTxns(0L)
                    .approvalRate(0.0)
                    .build();
        }

        long totalCount = transactions.size();
        double totalAmount = transactions.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0.0)
                .sum();
        double avgAmount = totalCount > 0 ? totalAmount / totalCount : 0.0;

        long approvedCount = transactions.stream()
                .filter(t -> t.getDecision() == Decision.APPROVE)
                .count();
        long reviewedCount = transactions.stream()
                .filter(t -> t.getDecision() == Decision.REVIEW)
                .count();
        long rejectedCount = transactions.stream()
                .filter(t -> t.getDecision() == Decision.REJECT)
                .count();

        double approvalRate = totalCount > 0 ? (double) approvedCount / totalCount : 0.0;

        return UserStatsSummary.builder()
                .totalTxns(totalCount)
                .totalAmount(totalAmount)
                .avgAmount(avgAmount)
                .approvedTxns(approvedCount)
                .reviewedTxns(reviewedCount)
                .rejectedTxns(rejectedCount)
                .approvalRate(approvalRate)
                .build();
    }

    /**
     * 计算时间窗口统计
     */
    private TimeWindowStats calculateTimeWindowStats(String userId) {
        long now = System.currentTimeMillis();

        // 24小时
        List<TransactionEntity> last24h = transactionRepository.findByUserIdAndEventTimestampBetween(
                userId, now - (24 * 60 * 60 * 1000), now);

        // 7天
        List<TransactionEntity> last7d = transactionRepository.findByUserIdAndEventTimestampBetween(
                userId, now - (7 * 24 * 60 * 60 * 1000), now);

        // 30天
        List<TransactionEntity> last30d = transactionRepository.findByUserIdAndEventTimestampBetween(
                userId, now - (30 * 24 * 60 * 60 * 1000), now);

        return TimeWindowStats.builder()
                .last24h(calculateWindowStats(last24h))
                .last7d(calculateWindowStats(last7d))
                .last30d(calculateWindowStats(last30d))
                .build();
    }

    /**
     * 计算单个窗口的统计
     */
    private TimeWindowStats.WindowStats calculateWindowStats(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return TimeWindowStats.WindowStats.builder()
                    .count(0L)
                    .totalAmount(0.0)
                    .avgAmount(0.0)
                    .build();
        }

        long count = transactions.size();
        double totalAmount = transactions.stream()
                .mapToDouble(t -> t.getAmount() != null ? t.getAmount().doubleValue() : 0.0)
                .sum();
        double avgAmount = count > 0 ? totalAmount / count : 0.0;

        return TimeWindowStats.WindowStats.builder()
                .count(count)
                .totalAmount(totalAmount)
                .avgAmount(avgAmount)
                .build();
    }
}
