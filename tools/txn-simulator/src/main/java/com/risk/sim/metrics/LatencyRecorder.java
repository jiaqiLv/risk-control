package com.risk.sim.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records and calculates latency statistics.
 * Thread-safe implementation for concurrent access.
 */
@Slf4j
@Component
public class LatencyRecorder {

    private final LongAdder count = new LongAdder();
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);

    // Histogram buckets (in milliseconds)
    private final ConcurrentHashMap<Long, AtomicLong> histogram = new ConcurrentHashMap<>();

    // Initialize histogram buckets
    {
        long[] buckets = {1, 5, 10, 25, 50, 75, 100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000, 5000};
        for (long bucket : buckets) {
            histogram.put(bucket, new AtomicLong(0));
        }
    }

    /**
     * Record a latency value.
     *
     * @param latencyMs Latency in milliseconds
     */
    public void record(long latencyMs) {
        count.increment();
        totalLatency.addAndGet(latencyMs);

        // Update min
        long currentMin = minLatency.get();
        while (latencyMs < currentMin && !minLatency.compareAndSet(currentMin, latencyMs)) {
            currentMin = minLatency.get();
        }

        // Update max
        long currentMax = maxLatency.get();
        while (latencyMs > currentMax && !maxLatency.compareAndSet(currentMax, latencyMs)) {
            currentMax = maxLatency.get();
        }

        // Update histogram
        updateHistogram(latencyMs);
    }

    private void updateHistogram(long latencyMs) {
        for (Long bucket : histogram.keySet().stream().sorted().toList()) {
            if (latencyMs <= bucket) {
                histogram.get(bucket).incrementAndGet();
                break;
            }
        }
    }

    /**
     * Get the total number of recorded latencies.
     *
     * @return Count
     */
    public long getCount() {
        return count.sum();
    }

    /**
     * Get the average latency.
     *
     * @return Average latency in milliseconds
     */
    public double getAverage() {
        long totalCount = count.sum();
        return totalCount > 0 ? (double) totalLatency.get() / totalCount : 0.0;
    }

    /**
     * Get the minimum latency.
     *
     * @return Minimum latency in milliseconds
     */
    public long getMin() {
        return minLatency.get();
    }

    /**
     * Get the maximum latency.
     *
     * @return Maximum latency in milliseconds
     */
    public long getMax() {
        return maxLatency.get();
    }

    /**
     * Calculate percentile approximation.
     * This is a simplified implementation using histogram data.
     *
     * @param percentile Percentile (0.0 to 1.0)
     * @return Approximate percentile value
     */
    public long getPercentile(double percentile) {
        long totalCount = count.sum();
        if (totalCount == 0) {
            return 0;
        }

        long targetCount = (long) (totalCount * percentile);
        long cumulativeCount = 0;

        for (Long bucket : histogram.keySet().stream().sorted().toList()) {
            cumulativeCount += histogram.get(bucket).get();
            if (cumulativeCount >= targetCount) {
                return bucket;
            }
        }

        return maxLatency.get();
    }

    /**
     * Get the P50 latency.
     */
    public long getP50() {
        return getPercentile(0.5);
    }

    /**
     * Get the P75 latency.
     */
    public long getP75() {
        return getPercentile(0.75);
    }

    /**
     * Get the P90 latency.
     */
    public long getP90() {
        return getPercentile(0.9);
    }

    /**
     * Get the P95 latency.
     */
    public long getP95() {
        return getPercentile(0.95);
    }

    /**
     * Get the P99 latency.
     */
    public long getP99() {
        return getPercentile(0.99);
    }

    /**
     * Get histogram data.
     *
     * @return Map of bucket -> count
     */
    public ConcurrentHashMap<Long, AtomicLong> getHistogram() {
        return histogram;
    }

    /**
     * Reset all statistics.
     */
    public void reset() {
        count.reset();
        totalLatency.set(0);
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0);

        for (AtomicLong bucketCount : histogram.values()) {
            bucketCount.set(0);
        }

        log.info("Latency recorder reset");
    }

    /**
     * Get a summary string of the statistics.
     *
     * @return Summary string
     */
    public String getSummary() {
        return String.format(
                "Latency Statistics: Count=%d, Avg=%.2fms, Min=%dms, Max=%dms, P50=%dms, P95=%dms, P99=%dms",
                getCount(), getAverage(), getMin(), getMax(), getP50(), getP95(), getP99()
        );
    }

    /**
     * Print statistics to log.
     */
    public void logStatistics() {
        log.info(getSummary());
        log.info("Histogram: {}", histogram);
    }
}
