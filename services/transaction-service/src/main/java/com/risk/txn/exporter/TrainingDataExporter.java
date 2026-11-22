package com.risk.txn.exporter;

import com.risk.data.entity.TransactionAttributeEntity;
import com.risk.data.entity.TransactionEntity;
import com.risk.data.entity.TransactionIdentityEntity;
import com.risk.data.repository.TransactionAttributeRepository;
import com.risk.data.repository.TransactionIdentityRepository;
import com.risk.data.repository.TransactionRepository;
import com.risk.txn.config.TransactionProperties;
import com.risk.txn.dto.ExportRequest;
import com.risk.txn.dto.ExportTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Service for exporting training data from transaction records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataExporter {

    private final TransactionRepository transactionRepository;
    private final TransactionAttributeRepository attributeRepository;
    private final TransactionIdentityRepository identityRepository;
    private final TransactionProperties properties;

    private final Map<String, ExportTask> tasks = new ConcurrentHashMap<>();

    /**
     * Create a new export task.
     */
    public ExportTask createExportTask(String taskId, ExportRequest request) {
        ExportTask task = ExportTask.builder()
            .taskId(taskId)
            .request(request)
            .status(ExportTask.ExportStatus.PROCESSING)
            .progress(0)
            .createdAt(Instant.now())
            .build();

        tasks.put(taskId, task);
        return task;
    }

    /**
     * Execute export task asynchronously.
     */
    @Async
    public void executeExport(ExportTask task) {
        try {
            log.info("Starting export task: {}", task.getTaskId());

            // 1. Query data
            List<TransactionEntity> transactions = queryTransactions(task);

            task.setTotalRecords((long) transactions.size());
            task.setProgress(10);

            // 2. Extract features and convert to training samples
            List<TrainingSample> samples = extractFeatures(transactions, task);

            task.setProgress(50);

            // 3. Export to file
            File outputFile = generateFile(samples, task.getRequest(), task);

            task.setProgress(100);
            task.setStatus(ExportTask.ExportStatus.COMPLETED);
            task.setFilePath(outputFile.getAbsolutePath());
            task.setFileSize(outputFile.length());
            task.setCompletedAt(Instant.now());

            log.info("Export task completed: {}, file: {}, size: {} bytes",
                task.getTaskId(), outputFile.getName(), outputFile.length());

        } catch (Exception e) {
            log.error("Export task failed: {}", task.getTaskId(), e);
            task.setStatus(ExportTask.ExportStatus.FAILED);
            task.setError(e.getClass().getSimpleName());
            task.setCompletedAt(Instant.now());
        }
    }

    /**
     * Query transactions based on export request.
     */
    private List<TransactionEntity> queryTransactions(ExportTask task) {
        ExportRequest request = task.getRequest();

        List<TransactionEntity> transactions;

        // Convert Instant to LocalDateTime
        LocalDateTime startDateTime = LocalDateTime.ofInstant(
            request.getStartTime(), java.time.ZoneId.systemDefault());
        LocalDateTime endDateTime = LocalDateTime.ofInstant(
            request.getEndTime(), java.time.ZoneId.systemDefault());

        // Query by time range
        if (request.getDecisionTypes() != null && !request.getDecisionTypes().isEmpty()) {
            transactions = transactionRepository.findByCreatedAtBetweenAndDecisionIn(
                startDateTime,
                endDateTime,
                request.getDecisionTypes()
            );
        } else {
            transactions = transactionRepository.findByCreatedAtBetween(
                startDateTime,
                endDateTime
            );
        }

        // Apply sample size limit
        if (request.getSampleSize() != null && transactions.size() > request.getSampleSize()) {
            transactions = transactions.stream()
                .limit(request.getSampleSize())
                .collect(Collectors.toList());
        }

        log.info("Queried {} transactions for export task: {}",
            transactions.size(), task.getTaskId());

        return transactions;
    }

    /**
     * Extract features from transactions.
     */
    private List<TrainingSample> extractFeatures(
            List<TransactionEntity> transactions,
            ExportTask task) {

        List<TrainingSample> samples = new ArrayList<>();

        for (int i = 0; i < transactions.size(); i++) {
            TransactionEntity tx = transactions.get(i);

            // Load attributes and identity
            Optional<TransactionAttributeEntity> attrOpt =
                attributeRepository.findByTransactionId(tx.getId());
            Optional<TransactionIdentityEntity> identityOpt =
                identityRepository.findByTransactionId(tx.getId());

            // Build feature map
            Map<String, Object> features = new HashMap<>();

            // Basic features
            features.put("amount", tx.getAmount());
            features.put("productCd", tx.getProductCd());
            features.put("eventTimestamp", tx.getEventTimestamp());

            // Add attributes
            attrOpt.ifPresent(attr -> {
                putIfNotNull(features, "card1", attr.getCard1());
                putIfNotNull(features, "card2", attr.getCard2());
                putIfNotNull(features, "card3", attr.getCard3());
                putIfNotNull(features, "card4", attr.getCard4());
                putIfNotNull(features, "card5", attr.getCard5());
                putIfNotNull(features, "card6", attr.getCard6());
                putIfNotNull(features, "addr1", attr.getAddr1());
                putIfNotNull(features, "addr2", attr.getAddr2());
                putIfNotNull(features, "dist1", attr.getDist1());
                putIfNotNull(features, "dist2", attr.getDist2());
            });

            // Add identity
            identityOpt.ifPresent(identity -> {
                putIfNotNull(features, "deviceType", identity.getDeviceType());
                putIfNotNull(features, "deviceInfo", identity.getDeviceInfo());
            });

            // Filter features if specific ones requested
            if (task.getRequest().getFeatures() != null &&
                !task.getRequest().getFeatures().isEmpty()) {
                features = features.entrySet().stream()
                    .filter(e -> task.getRequest().getFeatures().contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            // Create training sample
            TrainingSample sample = TrainingSample.builder()
                .label(tx.getIsFraud() ? 1 : 0)
                .features(features)
                .transactionId(tx.getTransactionId())
                .userId(tx.getUserId())
                .decision(tx.getDecision())
                .riskScore(tx.getRiskScore())
                .build();

            samples.add(sample);

            // Update progress
            if (i % 100 == 0) {
                task.setProgress(10 + (int) ((i * 40.0) / transactions.size()));
            }
        }

        // Balance samples if requested
        if (task.getRequest().getBalancePositive() &&
            Boolean.TRUE.equals(task.getRequest().getBalancePositive())) {
            samples = balanceSamples(samples);
        }

        return samples;
    }

    /**
     * Balance positive and negative samples.
     */
    private List<TrainingSample> balanceSamples(List<TrainingSample> samples) {
        List<TrainingSample> positive = samples.stream()
            .filter(s -> s.getLabel() == 1)
            .collect(Collectors.toList());

        List<TrainingSample> negative = samples.stream()
            .filter(s -> s.getLabel() == 0)
            .collect(Collectors.toList());

        int minSize = Math.min(positive.size(), negative.size());

        log.info("Balancing samples: positive={}, negative={}, balancedSize={}",
            positive.size(), negative.size(), minSize * 2);

        // Randomly sample to balance
        Collections.shuffle(positive);
        Collections.shuffle(negative);

        List<TrainingSample> balanced = new ArrayList<>();
        balanced.addAll(positive.subList(0, minSize));
        balanced.addAll(negative.subList(0, minSize));

        return balanced;
    }

    /**
     * Generate export file.
     */
    private File generateFile(List<TrainingSample> samples, ExportRequest request, ExportTask task)
            throws IOException {

        String format = request.getFormat().toUpperCase();
        String fileName = generateFileName(request, format);
        Path outputDir = Paths.get(properties.getExport().getOutputDir());
        File outputFile = outputDir.resolve(fileName).toFile();

        // Create output directory if not exists
        if (!outputDir.toFile().exists()) {
            outputDir.toFile().mkdirs();
        }

        if ("CSV".equals(format)) {
            generateCsv(samples, outputFile);
        } else if ("JSON".equals(format)) {
            // TODO: Implement JSON export
            throw new UnsupportedOperationException("JSON export not yet implemented");
        } else if ("PARQUET".equals(format)) {
            // TODO: Implement Parquet export
            throw new UnsupportedOperationException("Parquet export not yet implemented");
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        return outputFile;
    }

    /**
     * Generate CSV file.
     */
    private void generateCsv(List<TrainingSample> samples, File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader())) {

            // Get all feature keys
            Set<String> featureKeys = samples.stream()
                .flatMap(s -> s.getFeatures().keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new));

            // Write header
            List<String> header = new ArrayList<>();
            header.add("label");
            header.add("transactionId");
            header.add("userId");
            header.add("decision");
            header.add("riskScore");
            header.addAll(featureKeys);

            printer.printRecord(header);

            // Write data rows
            for (TrainingSample sample : samples) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(sample.getLabel()));
                row.add(sample.getTransactionId());
                row.add(sample.getUserId());
                row.add(sample.getDecision());
                row.add(String.valueOf(sample.getRiskScore()));

                // Add feature values
                for (String key : featureKeys) {
                    Object value = sample.getFeatures().get(key);
                    row.add(value != null ? value.toString() : "");
                }

                printer.printRecord(row);
            }

            log.info("Generated CSV file: {} with {} records", outputFile.getAbsolutePath(), samples.size());
        }
    }

    /**
     * Generate output file name.
     */
    private String generateFileName(ExportRequest request, String format) {
        if (request.getOutputFileName() != null && !request.getOutputFileName().isEmpty()) {
            return request.getOutputFileName();
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT
            .format(Instant.now())
            .replace(":", "-")
            .replace(".", "-");

        return String.format("training_data_%s.%s", timestamp, format.toLowerCase());
    }

    /**
     * Get export task status.
     */
    public ExportTaskStatus getTaskStatus(String taskId) {
        ExportTask task = tasks.get(taskId);

        if (task == null) {
            return null;
        }

        return ExportTaskStatus.builder()
            .taskId(task.getTaskId())
            .status(task.getStatus().name())
            .progress(task.getProgress())
            .totalRecords(task.getTotalRecords())
            .processedRecords(task.getProcessedRecords())
            .fileName(task.getFilePath() != null ?
                new File(task.getFilePath()).getName() : null)
            .downloadUrl(task.getFilePath() != null ?
                "/api/v1/training-data/download/" + taskId : null)
            .createdAt(task.getCreatedAt())
            .completedAt(task.getCompletedAt())
            .error(task.getError())
            .build();
    }

    /**
     * Get export file.
     */
    public File getExportFile(String taskId) {
        ExportTask task = tasks.get(taskId);
        if (task == null || task.getFilePath() == null) {
            return null;
        }
        return new File(task.getFilePath());
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
