package com.risk.sim.runner;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate limiter using Guava's RateLimiter.
 * Provides QPS control for transaction replay.
 */
@Slf4j
public class SimulationRateLimiter {

    private final RateLimiter rateLimiter;
    private final double permitsPerSecond;

    /**
     * Create a rate limiter with the specified QPS.
     *
     * @param permitsPerSecond Queries per second (QPS)
     */
    public SimulationRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        log.info("Created rate limiter: {} permits/second", permitsPerSecond);
    }

    /**
     * Acquire a permit, blocking if necessary.
     * This will block the calling thread until a permit is available.
     */
    public void acquire() {
        rateLimiter.acquire();
    }

    /**
     * Try to acquire a permit without blocking.
     *
     * @return true if a permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        return rateLimiter.tryAcquire();
    }

    /**
     * Try to acquire a permit with a timeout.
     *
     * @param timeout Timeout in seconds
     * @return true if a permit was acquired, false otherwise
     */
    public boolean tryAcquire(long timeout) {
        return rateLimiter.tryAcquire(timeout, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Acquire multiple permits at once.
     *
     * @param permits Number of permits to acquire
     */
    public void acquire(int permits) {
        rateLimiter.acquire(permits);
    }

    /**
     * Set a new rate.
     *
     * @param permitsPerSecond New rate in permits per second
     */
    public void setRate(double permitsPerSecond) {
        rateLimiter.setRate(permitsPerSecond);
        log.info("Updated rate limiter: {} permits/second", permitsPerSecond);
    }

    /**
     * Get the current rate.
     *
     * @return Current rate in permits per second
     */
    public double getRate() {
        return rateLimiter.getRate();
    }

    /**
     * Get the current QPS setting.
     *
     * @return QPS
     */
    public double getQps() {
        return permitsPerSecond;
    }
}
