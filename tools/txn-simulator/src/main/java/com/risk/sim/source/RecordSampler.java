package com.risk.sim.source;

import com.risk.sim.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Samples and filters transaction records based on configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordSampler {

    private final SimulatorProperties properties;

    /**
     * Sample records based on execution configuration.
     *
     * @param records All transaction records
     * @return Sampled list of records
     */
    public List<TransactionRecord> sample(List<TransactionRecord> records) {
        log.info("Sampling from {} records", records.size());

        int startIndex = properties.getExecution().getStartIndex();
        long maxRecords = properties.getExecution().getMaxRecords();

        // Apply start index
        List<TransactionRecord> sampled;
        if (startIndex > 0 && startIndex < records.size()) {
            sampled = records.subList(startIndex, records.size());
            log.debug("Applied start index: {}", startIndex);
        } else {
            sampled = records;
        }

        // Apply max records limit
        if (maxRecords > 0 && maxRecords < sampled.size()) {
            sampled = sampled.subList(0, (int) maxRecords);
            log.debug("Applied max records limit: {}", maxRecords);
        }

        log.info("Sampled {} records", sampled.size());
        return sampled;
    }

    /**
     * Filter records based on scenario configuration.
     *
     * @param records Transaction records
     * @return Filtered list of records
     */
    public List<TransactionRecord> filterByScenario(List<TransactionRecord> records) {
        SimulatorProperties.ScenarioType scenarioType = properties.getScenario().getType();

        log.info("Filtering records by scenario type: {}", scenarioType);

        return records.stream()
                .filter(record -> matchScenario(record, scenarioType))
                .collect(Collectors.toList());
    }

    private boolean matchScenario(TransactionRecord record, SimulatorProperties.ScenarioType scenarioType) {
        return switch (scenarioType) {
            case ALL -> true;
            case FRAUD_ONLY -> record.isFraud();
            case LEGIT_ONLY -> !record.isFraud();
            case COLD_START -> {
                // This will be handled separately in cold start simulation
                yield true;
            }
            case HIGH_MISSING_RATE -> {
                double missingRate = record.calculateMissingRate();
                yield missingRate > 0.3; // More than 30% missing
            }
        };
    }

    /**
     * Filter records by ProductCD.
     *
     * @param records   Transaction records
     * @param productCds List of ProductCD values to include
     * @return Filtered list of records
     */
    public List<TransactionRecord> filterByProductCd(List<TransactionRecord> records, List<String> productCds) {
        if (productCds == null || productCds.isEmpty()) {
            return records;
        }

        log.info("Filtering records by ProductCD: {}", productCds);

        return records.stream()
                .filter(record -> record.getProductCd() != null && productCds.contains(record.getProductCd()))
                .collect(Collectors.toList());
    }

    /**
     * Shuffle records randomly.
     *
     * @param records Transaction records
     * @return Shuffled list of records
     */
    public List<TransactionRecord> shuffle(List<TransactionRecord> records) {
        List<TransactionRecord> shuffled = new ArrayList<>(records);
        Collections.shuffle(shuffled);
        log.debug("Shuffled {} records", shuffled.size());
        return shuffled;
    }

    /**
     * Sort records by TransactionDT.
     *
     * @param records Transaction records
     * @return Sorted list of records
     */
    public List<TransactionRecord> sortByTransactionDt(List<TransactionRecord> records) {
        List<TransactionRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(TransactionRecord::getTransactionDt));
        log.debug("Sorted {} records by TransactionDT", sorted.size());
        return sorted;
    }

    /**
     * Split records into batches.
     *
     * @param records Transaction records
     * @return List of batches
     */
    public List<List<TransactionRecord>> batchRecords(List<TransactionRecord> records) {
        int batchSize = properties.getExecution().getBatchSize();
        List<List<TransactionRecord>> batches = new ArrayList<>();

        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            batches.add(records.subList(i, end));
        }

        log.info("Split {} records into {} batches of size ~{}",
                records.size(), batches.size(), batchSize);

        return batches;
    }

    /**
     * Get statistics about the records.
     *
     * @param records Transaction records
     * @return Statistics string
     */
    public String getStatistics(List<TransactionRecord> records) {
        long total = records.size();
        long fraudCount = records.stream().filter(TransactionRecord::isFraud).count();
        long legitCount = total - fraudCount;
        double fraudRate = total > 0 ? (double) fraudCount / total * 100.0 : 0.0;

        // Count by ProductCD
        Map<String, Long> countByProductCd = records.stream()
                .filter(r -> r.getProductCd() != null)
                .collect(Collectors.groupingBy(
                        TransactionRecord::getProductCd,
                        Collectors.counting()
                ));

        return String.format(
                "Total=%d, Fraud=%d (%.2f%%), Legit=%d, ProductCD distribution=%s",
                total, fraudCount, fraudRate, legitCount, countByProductCd
        );
    }
}
