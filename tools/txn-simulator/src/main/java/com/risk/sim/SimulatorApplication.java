package com.risk.sim;

import com.risk.sim.client.GrpcClientSender;
import com.risk.sim.client.HttpClientSender;
import com.risk.sim.client.SimulationResponse;
import com.risk.sim.config.SimulatorProperties;
import com.risk.sim.eval.EvaluationReport;
import com.risk.sim.eval.OfflineEvaluator;
import com.risk.sim.mapping.RequestMapper;
import com.risk.sim.metrics.LatencyRecorder;
import com.risk.sim.metrics.ResultSink;
import com.risk.sim.source.*;
import com.risk.sim.runner.ReplayEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transaction Simulator Application.
 * Simulates transaction requests using IEEE-CIS dataset.
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class SimulatorApplication {

    private final SimulatorProperties properties;
    private final CsvTransactionReader transactionReader;
    private final CsvIdentityReader identityReader;
    private final Joiner joiner;
    private final RecordSampler sampler;
    private final RequestMapper requestMapper;
    private final HttpClientSender httpClientSender;
    private final GrpcClientSender grpcClientSender;
    private final LatencyRecorder latencyRecorder;
    private final ResultSink resultSink;
    private final OfflineEvaluator offlineEvaluator;
    private final ReplayEngine replayEngine;

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    public CommandLineRunner run() {
        return args -> {
            log.info("========================================");
            log.info("  Transaction Simulator Starting...");
            log.info("  Mode: {}", properties.getMode());
            log.info("========================================");
            try {
                int successCount;

                if (properties.getMode() == SimulatorProperties.Mode.STREAMING) {
                    // STREAMING mode - use ReplayEngine which handles everything
                    successCount = replayEngine.replay(null, null);
                } else {
                    // Other modes - load data first, then use ReplayEngine
                    successCount = executeSimulationWithReplayEngine();
                }

                // Generate reports
                log.info("\nGenerating reports...");
                generateReports();
                log.info("\n========================================");
                log.info("  Simulation Completed Successfully!");
                log.info("  Successful requests: {}", successCount);
                log.info("========================================");
            } catch (Exception e) {
                log.error("Simulation failed", e);
                System.exit(1);
            } finally {
                // Cleanup
                resultSink.close();
                grpcClientSender.shutdown();
            }
        };
    }

    private int executeSimulation() throws Exception {
        // Step 1: Load CSV data
        log.info("Step 1: Loading CSV data...");
        Map<String, TransactionRecord> transactions = loadTransactionData();
        Map<String, TransactionRecord> identities = loadIdentityData();
        log.info("Sample Transaction Record: {}", transactions.values().stream().findFirst().orElse(null));
        log.info("Sample Identity Record: {}", identities.values().stream().findFirst().orElse(null));

        // Step 2: Join transaction and identity data
        log.info("\nStep 2: Joining transaction and identity data...");
        List<TransactionRecord> joinedRecords = joinData(transactions, identities);

        // Step 3: Sample and filter records
        log.info("\nStep 3: Sampling and filtering records...");
        List<TransactionRecord> sampledRecords = sampleAndFilterRecords(joinedRecords);

        // Step 4: Apply cold start simulation
        log.info("\nStep 4: Applying cold start simulation...");
        applyColdStart(sampledRecords);

        // Step 5: Initialize clients
        log.info("\nStep 5: Initializing clients...");
        initializeClients();

        // Step 6: Initialize result sink
        log.info("\nStep 6: Initializing result sink...");
        resultSink.initialize();

        // Step 7: Execute simulation
        log.info("\nStep 7: Executing simulation...");
        int successCount = executeSimulation(sampledRecords);
        return successCount;
    }

    private Map<String, TransactionRecord> loadTransactionData() throws Exception {
        String transactionPath = properties.getCsv().getTransactionPath();
        log.info("Loading transaction data from: {}", transactionPath);

        Map<String, TransactionRecord> transactions = transactionReader.readTransactions(transactionPath);

        log.info("Loaded {} transaction records", transactions.size());
        return transactions;
    }

    private Map<String, TransactionRecord> loadIdentityData() throws Exception {
        String identityPath = properties.getCsv().getIdentityPath();
        log.info("Loading identity data from: {}", identityPath);

        if (identityPath == null || identityPath.isEmpty()) {
            log.warn("No identity path specified, using only transaction data");
            return Collections.emptyMap();
        }

        Map<String, TransactionRecord> identities = identityReader.readIdentities(identityPath);

        log.info("Loaded {} identity records", identities.size());
        return identities;
    }

    private List<TransactionRecord> joinData(
            Map<String, TransactionRecord> transactions,
            Map<String, TransactionRecord> identities) {

        // Print join statistics
        String stats = joiner.getJoinStatistics(transactions, identities);
        log.info(stats);

        // Join records
        List<TransactionRecord> joinedRecords = joiner.join(transactions, identities);

        log.info("Joined {} records", joinedRecords.size());
        return joinedRecords;
    }

    private List<TransactionRecord> sampleAndFilterRecords(List<TransactionRecord> records) {
        // Filter by scenario
        List<TransactionRecord> filtered = sampler.filterByScenario(records);
        // Filter by ProductCD (ProductCD is now String type like "W", "H", "C", "S", "R")
        List<String> productCds = new ArrayList<>(properties.getScenario().getProductCds());
        if (!productCds.isEmpty()) {
            filtered = sampler.filterByProductCd(filtered, productCds);
        }
        // Sample
        List<TransactionRecord> sampled = sampler.sample(filtered);
        // Print statistics
        String stats = sampler.getStatistics(sampled);
        log.info("Record Statistics: {}", stats);
        return sampled;
    }

    private void applyColdStart(List<TransactionRecord> records) {

        double ratio = properties.getColdStart().getRatio();
        int coldStartCount = (int) (records.size() * ratio);

        log.info("Applying cold start simulation: {}% ({} records)",
                ratio * 100, coldStartCount);

        Random random = new Random();
        int applied = 0;

        for (TransactionRecord record : records) {
            if (random.nextDouble() < ratio) {
                String originalUserId = requestMapper.generateUserId(record);
                String coldStartUserId = requestMapper.generateColdStartUserId(originalUserId);
                // TODO: 完成冷启动数据模拟
                // In a real implementation, you would replace the user ID
                // For now, this is just a placeholder
                applied++;
            }
        }

        log.info("Applied cold start to {} records", applied);
    }

    private void initializeClients() {
        if (properties.getTarget().getType() == SimulatorProperties.TargetType.GATEWAY ||
            properties.getTarget().getType() == SimulatorProperties.TargetType.TRANSACTION_SERVICE) {

            httpClientSender.initialize(
                    properties.getTarget().getBaseUrl(),
                    properties.getTarget().getEndpoint(),
                    properties.getTarget().getTimeoutMs()
            );

            log.info("HTTP client initialized: {}{}",
                    properties.getTarget().getBaseUrl(),
                    properties.getTarget().getEndpoint());
        }
        
        // TODO: 将python端服务初始化配置项在配置文件中暴露出来
        if (properties.getTarget().getType() == SimulatorProperties.TargetType.PYTHON_INFERENCE) {
            // Parse host and port from base URL
            String baseUrl = properties.getTarget().getBaseUrl();
            String host = "localhost";
            int port = 50051;

            // Simple parsing (you may want to use URL class)
            if (baseUrl.contains(":")) {
                String[] parts = baseUrl.replace("http://", "").replace("https://", "").split(":");
                host = parts[0];
                if (parts.length > 1) {
                    port = Integer.parseInt(parts[1]);
                }
            }

            grpcClientSender.initialize(host, port, properties.getTarget().getTimeoutMs());

            log.info("gRPC client initialized: {}:{}", host, port);
        }
    }

    private int executeSimulation(List<TransactionRecord> records) {
        log.info("Starting simulation with {} records in {} mode",
                records.size(), properties.getMode());

        // Create sender function based on target type
        java.util.function.Function<TransactionRecord, SimulationResponse> senderFunc;

        if (properties.getTarget().getType() == SimulatorProperties.TargetType.GATEWAY ||
            properties.getTarget().getType() == SimulatorProperties.TargetType.TRANSACTION_SERVICE) {

            senderFunc = record -> {
                try {
                    Map<String, Object> request = requestMapper.mapToHttpRequest(record);
                    System.out.println("*request: " + request); // Debug: print the request
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
            };

        } else if (properties.getTarget().getType() == SimulatorProperties.TargetType.PYTHON_INFERENCE) {

            senderFunc = record -> {
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
            };

        } else {
            log.error("Unknown target type: {}", properties.getTarget().getType());
            return 0;
        }

        // Execute replay using ReplayEngine (would need to inject it)
        // For now, implement simple loop
        int successCount = 0;

        for (TransactionRecord record : records) {
            try {
                SimulationResponse response = senderFunc.apply(record);

                latencyRecorder.record(response.getLatencyMs());
                resultSink.write(record, response);

                if (response.isSuccess()) {
                    successCount++;
                }

                // Log progress
                if (records.indexOf(record) % 1000 == 0) {
                    log.info("Progress: {}/{} completed",
                            records.indexOf(record), records.size());
                }

            } catch (Exception e) {
                log.error("Error processing transaction: {}", record.getTransactionId(), e);
            }
        }

        return successCount;
    }

    /**
     * Execute simulation using ReplayEngine.
     * This method loads data, initializes clients, creates sender function,
     * and delegates to ReplayEngine for actual replay execution.
     */
    private int executeSimulationWithReplayEngine() throws Exception {
        // Step 1: Load CSV data
        log.info("Step 1: Loading CSV data...");
        Map<String, TransactionRecord> transactions = loadTransactionData();
        Map<String, TransactionRecord> identities = loadIdentityData();
        log.info("Sample Transaction Record: {}", transactions.values().stream().findFirst().orElse(null));
        log.info("Sample Identity Record: {}", identities.values().stream().findFirst().orElse(null));

        // Step 2: Join transaction and identity data
        log.info("\nStep 2: Joining transaction and identity data...");
        List<TransactionRecord> joinedRecords = joinData(transactions, identities);

        // Step 3: Sample and filter records
        log.info("\nStep 3: Sampling and filtering records...");
        List<TransactionRecord> sampledRecords = sampleAndFilterRecords(joinedRecords);

        // Step 4: Apply cold start simulation
        log.info("\nStep 4: Applying cold start simulation...");
        if (properties.getColdStart().isEnabled()) {
            applyColdStart(sampledRecords); // TODO: 完成冷启动逻辑
        }

        // Step 4.5: Sort by transactionDt for REPLAY_DT mode
        if (properties.getMode() == SimulatorProperties.Mode.REPLAY_DT) {
            log.info("\nStep 4.5: Sorting records by TransactionDt for REPLAY_DT mode...");
            sampledRecords.sort(java.util.Comparator.comparing(TransactionRecord::getTransactionDt));
            log.info("Sorted {} records by TransactionDt", sampledRecords.size());
            // Log first and last transaction timestamps
            if (!sampledRecords.isEmpty()) {
                log.info("Time range: {} to {}",
                        sampledRecords.get(0).getTransactionDt(),
                        sampledRecords.get(sampledRecords.size() - 1).getTransactionDt());
            }
        }

        // Step 5: Initialize clients
        log.info("\nStep 5: Initializing clients...");
        initializeClients();

        // Step 6: Initialize result sink
        log.info("\nStep 6: Initializing result sink...");
        resultSink.initialize();

        // Step 7: Create sender function based on target type
        log.info("\nStep 7: Creating sender function...");
        java.util.function.Function<TransactionRecord, SimulationResponse> senderFunc;

        if (properties.getTarget().getType() == SimulatorProperties.TargetType.GATEWAY ||
            properties.getTarget().getType() == SimulatorProperties.TargetType.TRANSACTION_SERVICE) {

            senderFunc = record -> {
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
            };

        } else if (properties.getTarget().getType() == SimulatorProperties.TargetType.PYTHON_INFERENCE) {

            senderFunc = record -> {
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
            };

        } else {
            log.error("Unknown target type: {}", properties.getTarget().getType());
            return 0;
        }

        // Step 8: Execute replay using ReplayEngine
        log.info("\nStep 8: Executing replay with ReplayEngine in {} mode...", properties.getMode());
        return replayEngine.replay(sampledRecords, senderFunc);
    }

    private void generateReports() {
        // Log latency statistics
        latencyRecorder.logStatistics();

        // Log result statistics
        String stats = resultSink.getStatistics();
        log.info(stats);

        // Generate offline evaluation
        EvaluationReport report = offlineEvaluator.evaluate(resultSink);
        String reportText = offlineEvaluator.generateReport(report);
        log.info("\n{}", reportText);
    }

    /**
     * Execute simulation using streaming approach with Reactor.
     * This enables high concurrency and non-blocking I/O.
     *
     * @return Number of successful requests
     */
    private int executeSimulationStreaming() {
        log.info("Starting STREAMING simulation with high concurrency");

        String transactionPath = properties.getCsv().getTransactionPath();
        String identityPath = properties.getCsv().getIdentityPath();

        // Load identity data into memory (usually smaller)
        Map<String, TransactionRecord> identities = Collections.emptyMap();
        if (identityPath != null && !identityPath.isEmpty()) {
            try {
                identities = identityReader.readIdentities(identityPath);
                log.info("Loaded {} identity records for streaming", identities.size());
            } catch (Exception e) {
                log.error("Failed to load identity data", e);
            }
        }

        // Create sender function based on target type
        java.util.function.Function<TransactionRecord, Mono<SimulationResponse>> senderFuncMono;

        if (properties.getTarget().getType() == SimulatorProperties.TargetType.GATEWAY ||
            properties.getTarget().getType() == SimulatorProperties.TargetType.TRANSACTION_SERVICE) {

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
                }).subscribeOn(Schedulers.boundedElastic());  // Execute on separate thread
            };

        } else if (properties.getTarget().getType() == SimulatorProperties.TargetType.PYTHON_INFERENCE) {

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
                // More sophisticated sampling can be implemented here
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
            String originalUserId = requestMapper.generateUserId(record);
            String coldStartUserId = requestMapper.generateColdStartUserId(originalUserId);
            // TODO: Apply the cold start transformation to the record
            // This would modify the record's user ID fields
            log.debug("Applied cold start for record: {} -> {}", originalUserId, coldStartUserId);
        }
    }
}
