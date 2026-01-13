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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.*;

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

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    public CommandLineRunner run() {
        return args -> {
            log.info("========================================");
            log.info("  Transaction Simulator Starting...");
            log.info("========================================");

            try {
                // Step 1: Load CSV data
                log.info("Step 1: Loading CSV data...");
                Map<String, TransactionRecord> transactions = loadTransactionData();
                Map<String, TransactionRecord> identities = loadIdentityData();

                // Step 2: Join transaction and identity data
                log.info("\nStep 2: Joining transaction and identity data...");
                List<TransactionRecord> joinedRecords = joinData(transactions, identities);

                System.out.println("Joined Records:");
                joinedRecords.forEach(System.out::println);

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

                // Step 8: Generate reports
                log.info("\nStep 8: Generating reports...");
                generateReports();

                log.info("\n========================================");
                log.info("  Simulation Completed Successfully!");
                log.info("  Total records processed: {}", sampledRecords.size());
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
        log.info(stats);

        return sampled;
    }

    private void applyColdStart(List<TransactionRecord> records) {
        if (!properties.getColdStart().isEnabled()) {
            return;
        }

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
}
