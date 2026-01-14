package com.risk.sim.runner;

import com.risk.sim.client.SimulationResponse;
import com.risk.sim.config.SimulatorProperties;
import com.risk.sim.metrics.LatencyRecorder;
import com.risk.sim.metrics.ResultSink;
import com.risk.sim.source.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Replay engine for executing transaction simulation.
 * Supports multiple replay modes: FIXED_QPS, REPLAY_DT, SCENARIO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayEngine {

    private final SimulatorProperties properties;
    private final LatencyRecorder latencyRecorder;
    private final ResultSink resultSink;

    private ExecutorService executorService;
    private SimulationRateLimiter rateLimiter;

    /**
     * Execute replay with the specified mode.
     *
     * @param records    Transaction records to replay
     * @param senderFunc Function to send transaction request
     * @return Number of successfully processed transactions
     */
    public int replay(
            List<TransactionRecord> records,
            Function<TransactionRecord, SimulationResponse> senderFunc) {

        String mode = properties.getMode().name();
        log.info("Starting replay in {} mode with {} records", mode, records.size());

        // Initialize rate limiter for FIXED_QPS mode
        if ("FIXED_QPS".equals(mode)) {
            double qps = properties.getRateControl().getQps();
            rateLimiter = new SimulationRateLimiter(qps);
        }

        // Initialize thread pool
        int concurrency = properties.getRateControl().getConcurrency();
        executorService = Executors.newFixedThreadPool(concurrency);

        int successCount = 0;

        try {
            switch (mode) {
                case "FIXED_QPS" -> successCount = replayFixedQps(records, senderFunc);
                case "REPLAY_DT" -> successCount = replayByTransactionDt(records, senderFunc);
                case "SCENARIO" -> successCount = replayScenario(records, senderFunc);
                default -> {
                    log.error("Unknown replay mode: {}", mode);
                    return 0;
                }
            }
        } finally {
            shutdown();
        }

        log.info("Replay completed. Success: {}, Total: {}", successCount, records.size());
        return successCount;
    }

    /**
     * Replay with fixed QPS.
     * Requests are sent at a constant rate specified by QPS.
     */
    private int replayFixedQps(
            List<TransactionRecord> records,
            Function<TransactionRecord, SimulationResponse> senderFunc) {

        AtomicInteger successCount = new AtomicInteger(0);
        int maxInFlight = properties.getRateControl().getMaxInFlight();
        Semaphore semaphore = new Semaphore(maxInFlight);

        for (TransactionRecord record : records) {
            try {
                // Wait for permit (rate limiting)
                rateLimiter.acquire();

                // Wait for concurrency slot
                semaphore.acquire();

                // Submit async task
                Future<Integer> future = executorService.submit(() -> {
                    try {
                        SimulationResponse response = senderFunc.apply(record);

                        // Record metrics
                        latencyRecorder.record(response.getLatencyMs());
                        resultSink.write(record, response);

                        if (response.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                        return response.isSuccess() ? 1 : 0;
                    } finally {
                        semaphore.release();
                    }
                });

                // Optionally wait for future completion
                // For max throughput, we don't wait here

            } catch (InterruptedException e) {
                log.error("Replay interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing transaction: {}", record.getTransactionId(), e);
            }
        }

        // Wait for all tasks to complete
        waitForCompletion();

        return successCount.get();
    }

    /**
     * Replay by TransactionDT with time acceleration.
     * Requests are sent with delays based on the time difference between consecutive transactions,
     * divided by a speed multiplier.
     */
    private int replayByTransactionDt(
            List<TransactionRecord> records,
            Function<TransactionRecord, SimulationResponse> senderFunc) {

        AtomicInteger successCount = new AtomicInteger(0);
        double speedMultiplier = properties.getTimeReplay().getSpeedMultiplier();
        int maxInFlight = properties.getRateControl().getMaxInFlight();
        Semaphore semaphore = new Semaphore(maxInFlight);

        Integer prevDt = null;

        for (TransactionRecord record : records) {
            try {
                // Calculate delay based on TransactionDT
                if (prevDt != null && record.getTransactionDt() != null) {
                    long dtDiff = record.getTransactionDt() - prevDt;
                    long delayMs = (long) ((dtDiff * 1000) / speedMultiplier);

                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }

                prevDt = record.getTransactionDt();

                // Wait for concurrency slot
                semaphore.acquire();

                // Submit async task
                executorService.submit(() -> {
                    try {
                        SimulationResponse response = senderFunc.apply(record);

                        // Record metrics
                        latencyRecorder.record(response.getLatencyMs());
                        resultSink.write(record, response);

                        if (response.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        semaphore.release();
                    }
                });

            } catch (InterruptedException e) {
                log.error("Replay interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing transaction: {}", record.getTransactionId(), e);
            }
        }

        // Wait for all tasks to complete
        waitForCompletion();

        return successCount.get();
    }

    /**
     * Replay for scenario testing.
     * Requests are sent as fast as possible (within concurrency limits).
     */
    private int replayScenario(
            List<TransactionRecord> records,
            Function<TransactionRecord, SimulationResponse> senderFunc) {

        int successCount = 0;
        int maxInFlight = properties.getRateControl().getMaxInFlight();
        Semaphore semaphore = new Semaphore(maxInFlight);

        List<Future<Integer>> futures = new ArrayList<>();

        for (TransactionRecord record : records) {
            try {
                // Wait for concurrency slot
                semaphore.acquire();

                // Submit async task
                Future<Integer> future = executorService.submit(() -> {
                    try {
                        SimulationResponse response = senderFunc.apply(record);

                        // Record metrics
                        latencyRecorder.record(response.getLatencyMs());
                        resultSink.write(record, response);

                        return response.isSuccess() ? 1 : 0;
                    } finally {
                        semaphore.release();
                    }
                });

                futures.add(future);

            } catch (InterruptedException e) {
                log.error("Replay interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing transaction: {}", record.getTransactionId(), e);
            }
        }

        // Collect results
        for (Future<Integer> future : futures) {
            try {
                successCount += future.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to get future result", e);
            }
        }

        return successCount;
    }

    /**
     * Wait for all submitted tasks to complete.
     */
    private void waitForCompletion() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
                log.warn("Executor did not terminate in 60 minutes");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Executor termination interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Shutdown the replay engine.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            log.info("Replay engine shutdown completed");
        }
    }
}
