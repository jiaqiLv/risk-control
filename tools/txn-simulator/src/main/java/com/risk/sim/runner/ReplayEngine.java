package com.risk.sim.runner;

import com.risk.sim.client.GrpcClientSender;
import com.risk.sim.client.HttpClientSender;
import com.risk.sim.client.SimulationResponse;
import com.risk.sim.config.SimulatorProperties;
import com.risk.sim.mapping.RequestMapper;
import com.risk.sim.metrics.LatencyRecorder;
import com.risk.sim.metrics.ResultSink;
import com.risk.sim.source.CsvIdentityReader;
import com.risk.sim.source.CsvTransactionReader;
import com.risk.sim.source.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Replay engine for executing transaction simulation.
 * Supports multiple replay modes: FIXED_QPS, REPLAY_DT, SCENARIO, COMMON, STREAMING.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayEngine {

    private final SimulatorProperties properties;
    private final LatencyRecorder latencyRecorder;
    private final ResultSink resultSink;

    // Optional dependencies for STREAMING mode
    @Autowired(required = false)
    private CsvTransactionReader transactionReader;

    @Autowired(required = false)
    private CsvIdentityReader identityReader;

    @Autowired(required = false)
    private RequestMapper requestMapper;

    @Autowired(required = false)
    private HttpClientSender httpClientSender;

    @Autowired(required = false)
    private GrpcClientSender grpcClientSender;

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
                case "COMMON" -> successCount = replayCommon(records, senderFunc);
                case "STREAMING" -> {
                    if (records != null && !records.isEmpty()) {
                        log.warn("STREAMING mode ignores pre-loaded records, reading directly from CSV");
                    }
                    successCount = replayStreaming();
                }
                default -> {
                    log.error("Unknown replay mode: {}", mode);
                    return 0;
                }
            }
        } finally {
            shutdown();
        }

        log.info("Replay completed. Success: {}, Total: {}", successCount,
                records != null ? records.size() : "N/A (STREAMING mode)");
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
                // semaphore.acquire();
                if (!semaphore.tryAcquire(1, TimeUnit.SECONDS)) {
                    log.warn("Max in-flight reached, dropping request: {}", record.getTransactionId());
                    continue;
                }

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
                        return response.isSuccess() ? 1 : 0;
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

        Long prevDt = null;

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
     * Common replay mode.
     * Simple sequential processing without rate limiting.
     * This is the original executeSimulation() implementation.
     */
    private int replayCommon(
            List<TransactionRecord> records,
            Function<TransactionRecord, SimulationResponse> senderFunc) {

        int successCount = 0;

        for (TransactionRecord record : records) {
            try {
                SimulationResponse response = senderFunc.apply(record);

                // Record metrics
                latencyRecorder.record(response.getLatencyMs());
                resultSink.write(record, response);

                if (response.isSuccess()) {
                    successCount++;
                }

                // Log progress
                if (records.indexOf(record) % 1000 == 0) {
                    log.info("COMMON mode progress: {}/{} completed",
                            records.indexOf(record), records.size());
                }

            } catch (Exception e) {
                log.error("Error processing transaction: {}", record.getTransactionId(), e);
            }
        }

        return successCount;
    }

    /**
     * Streaming replay mode using Reactor.
     * Enables high concurrency and non-blocking I/O.
     * This is the original executeSimulationStreaming() implementation.
     */
    private int replayStreaming() {
        log.info("Starting STREAMING mode with high concurrency");

        // Check required dependencies
        if (transactionReader == null || requestMapper == null) {
            log.error("STREAMING mode requires transactionReader and requestMapper");
            return 0;
        }

        String transactionPath = properties.getCsv().getTransactionPath();
        String identityPath = properties.getCsv().getIdentityPath();

        // Load identity data into memory (usually smaller)
        Map<String, TransactionRecord> identities = Collections.emptyMap();
        if (identityPath != null && !identityPath.isEmpty()) {
            try {
                if (identityReader != null) {
                    identities = identityReader.readIdentities(identityPath);
                    log.info("Loaded {} identity records for streaming", identities.size());
                }
            } catch (Exception e) {
                log.error("Failed to load identity data", e);
            }
        }

        // Create sender function based on target type
        java.util.function.Function<TransactionRecord, Mono<SimulationResponse>> senderFuncMono;

        if (properties.getTarget().getType() == SimulatorProperties.TargetType.GATEWAY ||
            properties.getTarget().getType() == SimulatorProperties.TargetType.TRANSACTION_SERVICE) {

            if (httpClientSender == null) {
                log.error("HTTP client sender not available");
                return 0;
            }

            senderFuncMono = record -> {
                return Mono.fromCallable(() -> {
                    try {
                        Map<String, Object> request = requestMapper.mapToHttpRequest(record);
                        return httpClientSender.send(request);
                    } catch (Exception e) {
                        log.error("Failed to send request for transaction: {}", record.getTransactionId(), e);
                        return SimulationResponse.builder()
                                .success(false)
                                .latencyMs(0)
                                .statusCode(-1)
                                .errorMessage(e.getMessage())
                                .build();
                    }
                }).subscribeOn(Schedulers.boundedElastic());
            };

        } else if (properties.getTarget().getType() == SimulatorProperties.TargetType.PYTHON_INFERENCE) {

            if (grpcClientSender == null) {
                log.error("gRPC client sender not available");
                return 0;
            }

            senderFuncMono = record -> {
                return Mono.fromCallable(() -> {
                    try {
                        var request = requestMapper.mapToGrpcRequest(record);
                        return grpcClientSender.send(request);
                    } catch (Exception e) {
                        log.error("Failed to send gRPC request for transaction: {}", record.getTransactionId(), e);
                        return SimulationResponse.builder()
                                .success(false)
                                .latencyMs(0)
                                .statusCode(-1)
                                .errorMessage(e.getMessage())
                                .build();
                    }
                }).subscribeOn(Schedulers.boundedElastic());
            };

        } else {
            log.error("Unknown target type: {}", properties.getTarget().getType());
            return 0;
        }

        // Counters for statistics
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        // Get concurrency settings
        int concurrency = properties.getRateControl().getConcurrency();
        int maxInFlight = properties.getRateControl().getMaxInFlight();

        log.info("Streaming settings: concurrency={}, maxInFlight={}", concurrency, maxInFlight);

        // Create streaming pipeline
        Flux<TransactionRecord> recordStream = transactionReader.readTransactionsStreamWithJoin(transactionPath, identities);

        recordStream
            // Apply filtering
            .filter(this::shouldIncludeRecord)
            // Apply sampling (if needed)
            .filter(record -> {
                // Simple random sampling for streaming
                return true;
            })
            // Apply cold start (if enabled)
            .doOnNext(record -> {
                if (properties.getColdStart().isEnabled()) {
                    applyColdStartToRecord(record);
                }
            })
            // Buffer for batch processing
            .buffer(100)  // Process 100 records at a time
            // Process each batch with concurrency
            .flatMap(batch -> {
                return Flux.fromIterable(batch)
                    .flatMap(record -> {
                        return senderFuncMono.apply(record)
                            .doOnSuccess(response -> {
                                // Record metrics
                                latencyRecorder.record(response.getLatencyMs());
                                resultSink.write(record, response);

                                if (response.isSuccess()) {
                                    successCount.incrementAndGet();
                                }

                                // Log progress
                                int current = processedCount.incrementAndGet();
                                if (current % 1000 == 0) {
                                    log.info("Streaming progress: {} records processed", current);
                                }
                            })
                            .onErrorResume(e -> {
                                log.error("Error processing transaction: {}", record.getTransactionId(), e);
                                return Mono.empty();
                            });
                    }, concurrency)  // Concurrent requests per batch
                    ;
            }, maxInFlight)  // Max batches in flight
            .doOnComplete(() -> {
                log.info("Streaming simulation completed!");
            })
            .doOnError(e -> {
                log.error("Streaming simulation failed", e);
            })
            .blockLast();  // Wait for completion

        log.info("Final statistics - Processed: {}, Success: {}", processedCount.get(), successCount.get());
        return successCount.get();
    }

    /**
     * Check if a record should be included based on scenario filters.
     */
    private boolean shouldIncludeRecord(TransactionRecord record) {
        SimulatorProperties.ScenarioType scenarioType = properties.getScenario().getType();

        // Apply scenario-based filtering
        if (scenarioType == SimulatorProperties.ScenarioType.FRAUD_ONLY && !record.isFraud()) {
            return false;
        }
        if (scenarioType == SimulatorProperties.ScenarioType.LEGIT_ONLY && record.isFraud()) {
            return false;
        }

        // Apply ProductCD filtering
        List<String> productCds = properties.getScenario().getProductCds();
        if (!productCds.isEmpty() && !productCds.contains(record.getProductCd())) {
            return false;
        }

        return true;
    }

    /**
     * Apply cold start transformation to a single record.
     */
    private void applyColdStartToRecord(TransactionRecord record) {
        double ratio = properties.getColdStart().getRatio();
        Random random = new Random();

        if (random.nextDouble() < ratio) {
            if (requestMapper != null) {
                String originalUserId = requestMapper.generateUserId(record);
                String coldStartUserId = requestMapper.generateColdStartUserId(originalUserId);
                // TODO: Apply the cold start transformation to the record
                log.debug("Applied cold start for record: {} -> {}", originalUserId, coldStartUserId);
            }
        }
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
