package com.risk.sim.source;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.risk.sim.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Component
public class CsvTransactionReader {

    private final SimulatorProperties simulatorProperties;
    private final Map<String, TransactionRecord> records = new HashMap<>();

    public CsvTransactionReader(SimulatorProperties simulatorProperties) {
        this.simulatorProperties = simulatorProperties;
    }

    /**
     * Read transaction CSV file and parse records.
     *
     * @param filePath Path to the CSV file
     * @return Map of TransactionID -> TransactionRecord
     */
    public Map<String, TransactionRecord> readTransactions(String filePath) throws IOException, CsvValidationException {
        log.info("Reading transaction CSV from: {}", filePath);

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] header = reader.readNext();
            if (header == null) {
                log.error("Empty CSV file");
                return records;
            }

            // Create header index map for efficient lookup
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                // Store both original and lowercase version for flexible lookup
                headerIndex.put(header[i], i);
                headerIndex.put(header[i].toLowerCase(), i); // for case-insensitive access
            }

            String[] row;
            int count = 0;
            double sampleRate = simulatorProperties.getCsv().getDataSampleRate();
            log.info("Reading CSV with sample rate: {} ({}%)", sampleRate, sampleRate * 100);
            while ((row = reader.readNext()) != null) {
                // Apply data sampling based on configured rate
                if (ThreadLocalRandom.current().nextDouble() > sampleRate) {
                    continue;  // Skip this row based on sampling rate
                }
                try {
                    TransactionRecord record = parseTransactionRow(row, headerIndex);
                    if (record != null && record.getTransactionId() != null) {
                        records.put(record.getTransactionId(), record);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse row {}: {}", count, e.getMessage());
                }
            }
            log.info("Successfully loaded {} transaction records", count);
        }

        return records;
    }

    private TransactionRecord parseTransactionRow(String[] row, Map<String, Integer> headerIndex) {
        TransactionRecord record = new TransactionRecord();

        record.setTransactionId(getField(row, headerIndex, "TransactionID"));
        record.setFraud(parseBoolean(getField(row, headerIndex, "isFraud")));
        record.setTransactionDt(parseDouble(getField(row, headerIndex, "TransactionDT")));
        record.setTransactionAmt(parseBigDecimal(getField(row, headerIndex, "TransactionAmt")));
        record.setProductCd(getString(getField(row, headerIndex, "ProductCD")));

        // Email domain fields
        record.setPEmailDomain(getField(row, headerIndex, "P_emaildomain"));
        record.setREmailDomain(getField(row, headerIndex, "R_emaildomain"));

        // Card fields
        record.setCard1(parseDouble(getField(row, headerIndex, "card1")));
        record.setCard2(parseDouble(getField(row, headerIndex, "card2")));
        record.setCard3(parseDouble(getField(row, headerIndex, "card3")));
        record.setCard4(getField(row, headerIndex, "card4"));
        record.setCard5(parseDouble(getField(row, headerIndex, "card5")));
        record.setCard6(getField(row, headerIndex, "card6"));

        // Address fields
        record.setAddr1(parseDouble(getField(row, headerIndex, "addr1")));
        record.setAddr2(parseDouble(getField(row, headerIndex, "addr2")));

        // Distance fields
        record.setDist1(parseDouble(getField(row, headerIndex, "dist1")));
        record.setDist2(parseDouble(getField(row, headerIndex, "dist2")));

        // C fields (count of something)
        for (int i = 1; i <= 14; i++) {
            setDoubleField(record, "c" + i, parseDouble(getField(row, headerIndex, "C" + i)));
        }

        // D fields (date/time delta)
        for (int i = 1; i <= 15; i++) {
            setDoubleField(record, "d" + i, parseDouble(getField(row, headerIndex, "D" + i)));
        }

        // M fields (match) - String values like "T", "F", "M2"
        for (int i = 1; i <= 9; i++) {
            setStringField(record, "m" + i, getString(getField(row, headerIndex, "M" + i)));
        }

        // V fields (Vesta engineered features)Y
        for (int i = 1; i <= 339; i++) {
            setDoubleField(record, "v" + i, parseDouble(getField(row, headerIndex, "V" + i)));
        }
        return record;
    }

    private String getField(String[] row, Map<String, Integer> headerIndex, String fieldName) {
        Integer index = headerIndex.get(fieldName);
        if (index == null || index >= row.length) {
            return null;
        }
        String value = row[index];
        return (value == null || value.isEmpty()) ? null : value;
    }

    // private Integer parseInteger(String value) {
    //     if (value == null || value.isEmpty()) return null;
    //     try {
    //         return Integer.parseInt(value);
    //     } catch (NumberFormatException e) {
    //         return null;
    //     }
    // }

    private Double parseDouble(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) return false;
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private String getString(String value) {
        // Return the value as-is, or empty string if null/empty
        return (value == null || value.isEmpty()) ? null : value;
    }

    private void setStringField(TransactionRecord record, String fieldName, String value) {
        try {
            Field field = TransactionRecord.FIELD_CACHE.get(fieldName.toLowerCase());
            if (field != null) {
                field.set(record, value);
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to set string field {}: {}", fieldName, e.getMessage());
        }
    }

    // private void setIntField(TransactionRecord record, String fieldName, Integer value) {
    //     try {
    //         Field field = TransactionRecord.FIELD_CACHE.get(fieldName.toLowerCase());
    //         if (field != null) {
    //             field.set(record, value);
    //         }
    //     } catch (IllegalAccessException e) {
    //         log.warn("Failed to set int field {}: {}", fieldName, e.getMessage());
    //     }
    // }

    private void setDoubleField(TransactionRecord record, String fieldName, Double value) {
        try {
            Field field = TransactionRecord.FIELD_CACHE.get(fieldName.toLowerCase());
            if (field != null) {
                field.set(record, value);
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to set double field {}: {}", fieldName, e.getMessage());
        }
    }

    public int getRecordCount() {
        return records.size();
    }

    public Collection<TransactionRecord> getAllRecords() {
        return records.values();
    }

    /**
     * Stream transaction records from CSV file asynchronously.
     * Records are emitted as they are read, enabling streaming processing.
     *
     * @param filePath Path to the CSV file
     * @return Flux of TransactionRecord
     */
    public Flux<TransactionRecord> readTransactionsStream(String filePath) {
        Sinks.Many<TransactionRecord> sink = Sinks.many().multicast().onBackpressureBuffer();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "csv-reader-thread");
            thread.setDaemon(false);
            return thread;
        });

        executor.submit(() -> {
            log.info("Starting to stream transaction CSV from: {}", filePath);
            try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
                String[] header = reader.readNext();
                if (header == null) {
                    log.error("Empty CSV file");
                    sink.tryEmitComplete();
                    return;
                }

                // Create header index map for efficient lookup (case-insensitive)
                Map<String, Integer> headerIndex = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    // Store both original and lowercase version for flexible lookup
                    headerIndex.put(header[i], i);
                    headerIndex.put(header[i].toLowerCase(), i);
                }
                log.info("Header index: {}", headerIndex);

                String[] row;
                int count = 0;
                int batchSize = 1000;
                double sampleRate = simulatorProperties.getCsv().getDataSampleRate();
                log.info("Streaming CSV with sample rate: {} ({}%)", sampleRate, sampleRate * 100);

                while ((row = reader.readNext()) != null) {
                    // Apply data sampling based on configured rate
                    if (ThreadLocalRandom.current().nextDouble() > sampleRate) {
                        continue;  // Skip this row based on sampling rate
                    }

                    try {
                        TransactionRecord record = parseTransactionRow(row, headerIndex);
                        if (record != null && record.getTransactionId() != null) {
                            sink.tryEmitNext(record);
                            count++;

                            // Log progress every batch
                            if (count % batchSize == 0) {
                                log.info("Streamed {} records so far...", count);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse row {}: {}", count, e.getMessage());
                    }
                }

                log.info("Finished streaming {} transaction records", count);
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Error streaming CSV", e);
                sink.tryEmitError(e);
            } finally {
                executor.shutdown();
            }
        });

        return sink.asFlux();
    }

    /**
     * Stream transaction records from CSV file asynchronously with identity join.
     *
     * @param filePath    Path to the transaction CSV file
     * @param identityMap Map of identity data (can be empty)
     * @return Flux of TransactionRecord
     */
    public Flux<TransactionRecord> readTransactionsStreamWithJoin(
            String filePath,
            Map<String, TransactionRecord> identityMap) {

        return readTransactionsStream(filePath)
                .map(transaction -> {
                    // Join with identity data if available
                    if (identityMap != null && !identityMap.isEmpty()) {
                        TransactionRecord identity = identityMap.get(transaction.getTransactionId());
                        if (identity != null) {
                            // Merge identity fields into transaction record
                            transaction.setDeviceType(identity.getDeviceType());
                            transaction.setDeviceInfo(identity.getDeviceInfo());
                            // Add other identity fields as needed
                        }
                    }
                    return transaction;
                });
    }
}
