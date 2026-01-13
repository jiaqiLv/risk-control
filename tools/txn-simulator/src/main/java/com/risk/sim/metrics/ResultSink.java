package com.risk.sim.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.risk.sim.client.SimulationResponse;
import com.risk.sim.config.SimulatorProperties;
import com.risk.sim.source.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes simulation results to output files.
 * Supports CSV, JSONL, and JSON formats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultSink {

    private final SimulatorProperties properties;
    private final ObjectMapper objectMapper;

    private BufferedWriter writer;
    private long recordCount = 0;

    // In-memory statistics
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong approveCount = new AtomicLong(0);
    private final AtomicLong rejectCount = new AtomicLong(0);
    private final AtomicLong reviewCount = new AtomicLong(0);

    // True positive/negative counters (for fraud detection)
    private final AtomicLong truePositives = new AtomicLong(0);
    private final AtomicLong trueNegatives = new AtomicLong(0);
    private final AtomicLong falsePositives = new AtomicLong(0);
    private final AtomicLong falseNegatives = new AtomicLong(0);

    /**
     * Initialize the result sink.
     * Creates output directory and opens file for writing.
     */
    public void initialize() throws IOException {
        String outputPath = properties.getOutput().getPath();
        String format = properties.getOutput().getFormat();

        // Create output directory
        Path outputDir = Paths.get(outputPath).getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }

        // Create output file with timestamp
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fullOutputPath = outputPath + "_" + timestamp + "." + format.toLowerCase();

        writer = new BufferedWriter(new FileWriter(fullOutputPath));

        // Write header for CSV format
        if ("CSV".equalsIgnoreCase(format)) {
            writeCsvHeader();
        }

        log.info("Initialized result sink: {}", fullOutputPath);
    }

    /**
     * Write a simulation result.
     *
     * @param record   Transaction record
     * @param response Simulation response
     */
    public synchronized void write(TransactionRecord record, SimulationResponse response) {
        try {
            if (writer == null) {
                log.warn("Result sink not initialized");
                return;
            }

            String format = properties.getOutput().getFormat();

            switch (format.toUpperCase()) {
                case "CSV" -> writeCsvLine(record, response);
                case "JSONL" -> writeJsonlLine(record, response);
                case "JSON" -> throw new UnsupportedOperationException("JSON format requires batch writing");
                default -> log.warn("Unknown output format: {}", format);
            }

            // Update statistics
            updateStatistics(record, response);

            recordCount++;

            // Flush periodically
            if (recordCount % 1000 == 0) {
                writer.flush();
                log.debug("Written {} records", recordCount);
            }

        } catch (IOException e) {
            log.error("Failed to write result", e);
        }
    }

    private void writeCsvHeader() throws IOException {
        StringBuilder header = new StringBuilder();
        header.append("transactionId,isFraud");

        if (properties.getOutput().isIncludeRequest()) {
            header.append(",transactionAmt,productCd,card1,addr1");
        }

        if (properties.getOutput().isIncludeResponse()) {
            header.append(",success,statusCode,decision,riskScore");
        }

        if (properties.getOutput().isIncludeLatency()) {
            header.append(",latencyMs");
        }

        header.append("\n");
        writer.write(header.toString());
    }

    private void writeCsvLine(TransactionRecord record, SimulationResponse response) throws IOException {
        StringBuilder line = new StringBuilder();
        line.append(record.getTransactionId()).append(",");
        line.append(record.isFraud() ? "1" : "0");

        if (properties.getOutput().isIncludeRequest()) {
            line.append(",").append(escapeCsv(record.getTransactionAmt()));
            line.append(",").append(escapeCsv(record.getProductCd()));
            line.append(",").append(escapeCsv(record.getCard1()));
            line.append(",").append(escapeCsv(record.getAddr1()));
        }

        if (properties.getOutput().isIncludeResponse()) {
            line.append(",").append(response.isSuccess() ? "1" : "0");
            line.append(",").append(response.getStatusCode());
            line.append(",").append(escapeCsv(response.getDecision()));
            line.append(",").append(escapeCsv(response.getRiskScore()));
        }

        if (properties.getOutput().isIncludeLatency()) {
            line.append(",").append(response.getLatencyMs());
        }

        line.append("\n");
        writer.write(line.toString());
    }

    private void writeJsonlLine(TransactionRecord record, SimulationResponse response) throws IOException {
        Map<String, Object> result = new ConcurrentHashMap<>();

        List<String> outputFields = properties.getOutput().getOutputFields();

        if (outputFields == null || outputFields.isEmpty()) {
            // If no fields specified, use default behavior
            result.put("transactionId", record.getTransactionId());
            result.put("isFraud", record.isFraud());

            if (properties.getOutput().isIncludeRequest()) {
                Map<String, Object> request = new ConcurrentHashMap<>();
                request.put("transactionAmt", record.getTransactionAmt() != null ? record.getTransactionAmt() : "");
                request.put("productCd", record.getProductCd() != null ? record.getProductCd() : "");
                request.put("card1", record.getCard1() != null ? record.getCard1() : "");
                request.put("addr1", record.getAddr1() != null ? record.getAddr1() : "");
                result.put("request", request);
            }
        } else {
            // Use configured fields
            for (String fieldName : outputFields) {
                Object value = getFieldValue(record, fieldName);
                if (value != null) {
                    result.put(fieldName, value);
                } else {
                    result.put(fieldName, "");
                }
            }
        }

        if (properties.getOutput().isIncludeResponse()) {
            Map<String, Object> resp = new ConcurrentHashMap<>();
            resp.put("success", response.isSuccess());
            resp.put("statusCode", response.getStatusCode());
            resp.put("decision", response.getDecision() != null ? response.getDecision() : "");
            resp.put("riskScore", response.getRiskScore() != null ? response.getRiskScore() : "");
            result.put("response", resp);
        }

        if (properties.getOutput().isIncludeLatency()) {
            result.put("latencyMs", response.getLatencyMs());
        }

        writer.write(objectMapper.writeValueAsString(result) + "\n");
    }

    /**
     * Get field value from TransactionRecord using reflection.
     * Handles special cases for boolean fields (isFraud -> isFraud).
     */
    private Object getFieldValue(TransactionRecord record, String fieldName) {
        try {
            // Special case for isFraud
            if ("isFraud".equals(fieldName)) {
                return record.isFraud();
            }

            // Try to find the field by name
            Field field = findField(record.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(record);
            }

            log.warn("Field not found: {}", fieldName);
            return null;
        } catch (IllegalAccessException e) {
            log.warn("Failed to access field: {}", fieldName, e);
            return null;
        }
    }

    /**
     * Find a field in the class hierarchy, including parent classes.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Field not found in this class, try parent class
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private void updateStatistics(TransactionRecord record, SimulationResponse response) {
        if (response.isSuccess()) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }

        if (response.isApproved()) {
            approveCount.incrementAndGet();
        } else if (response.isRejected()) {
            rejectCount.incrementAndGet();
        } else if (response.isReview()) {
            reviewCount.incrementAndGet();
        }

        // Calculate confusion matrix
        boolean actualFraud = record.isFraud();
        boolean predictedFraud = response.isRejected() || (response.getRiskScore() != null && response.getRiskScore() > 0.5);

        if (actualFraud && predictedFraud) {
            truePositives.incrementAndGet();
        } else if (!actualFraud && !predictedFraud) {
            trueNegatives.incrementAndGet();
        } else if (!actualFraud && predictedFraud) {
            falsePositives.incrementAndGet();
        } else {
            falseNegatives.incrementAndGet();
        }
    }

    /**
     * Close the result sink and finalize output.
     */
    public void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                log.info("Closed result sink. Total records written: {}", recordCount);
            }
        } catch (IOException e) {
            log.error("Failed to close result sink", e);
        }
    }

    /**
     * Get statistics summary.
     *
     * @return Statistics string
     */
    public String getStatistics() {
        long total = successCount.get() + failureCount.get();

        double accuracy = total > 0 ? (double) (truePositives.get() + trueNegatives.get()) / total * 100.0 : 0.0;
        double precision = (truePositives.get() + falsePositives.get()) > 0
                ? (double) truePositives.get() / (truePositives.get() + falsePositives.get()) * 100.0 : 0.0;
        double recall = (truePositives.get() + falseNegatives.get()) > 0
                ? (double) truePositives.get() / (truePositives.get() + falseNegatives.get()) * 100.0 : 0.0;

        return String.format(
                "Result Statistics: Total=%d, Success=%d, Failure=%d, " +
                        "Approve=%d, Reject=%d, Review=%d, " +
                        "Accuracy=%.2f%%, Precision=%.2f%%, Recall=%.2f%%",
                total, successCount.get(), failureCount.get(),
                approveCount.get(), rejectCount.get(), reviewCount.get(),
                accuracy, precision, recall
        );
    }
}
