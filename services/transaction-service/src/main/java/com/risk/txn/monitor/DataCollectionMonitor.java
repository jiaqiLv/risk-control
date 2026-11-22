package com.risk.txn.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitor for data collection metrics and alerting.
 */
@Slf4j
@Component
public class DataCollectionMonitor {

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private volatile long lastAlertTime = 0;

    private static final double FAILURE_RATE_THRESHOLD = 0.05; // 5%
    private static final long ALERT_COOLDOWN_MS = 300000; // 5 minutes

    /**
     * Record successful event processing.
     */
    public void recordSuccess(int count) {
        successCount.addAndGet(count);
        log.debug("Success count: {}", successCount.get());
    }

    /**
     * Record failed event processing.
     */
    public void recordFailure(int count, Exception e) {
        long failures = failureCount.addAndGet(count);
        long successes = successCount.get();
        double failureRate = (double) failures / (successes + failures);

        log.error("Event processing failed: count={}, totalFailures={}, failureRate={}, error={}",
            count, failures, String.format("%.2f%%", failureRate * 100), e.getMessage());

        // Check if alert is needed
        if (failureRate > FAILURE_RATE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastAlertTime > ALERT_COOLDOWN_MS) {
                sendAlert(String.format(
                    "High failure rate detected: %.2f%% (%d failures out of %d total)",
                    failureRate * 100, failures, successes + failures
                ));
                lastAlertTime = now;
            }
        }
    }

    /**
     * Get current statistics.
     */
    public Statistics getStatistics() {
        long successes = successCount.get();
        long failures = failureCount.get();
        long total = successes + failures;

        return Statistics.builder()
            .totalEvents(total)
            .successEvents(successes)
            .failureEvents(failures)
            .failureRate(total > 0 ? (double) failures / total : 0.0)
            .build();
    }

    /**
     * Reset statistics.
     */
    public void reset() {
        successCount.set(0);
        failureCount.set(0);
        log.info("Statistics reset");
    }

    /**
     * Send alert notification.
     * TODO: Integrate with alert system (email, DingTalk, WeChat Work, etc.)
     */
    private void sendAlert(String message) {
        log.warn("ALERT: {}", message);

        // TODO: Implement actual alert notification
        // Options:
        // 1. Email via JavaMailSender
        // 2. DingTalk webhook
        // 3. WeChat Work webhook
        // 4. Slack webhook
        // 5. PagerDuty
    }

    /**
     * Statistics data class.
     */
    @lombok.Data
    @lombok.Builder
    public static class Statistics {
        private long totalEvents;
        private long successEvents;
        private long failureEvents;
        private double failureRate;
    }
}
