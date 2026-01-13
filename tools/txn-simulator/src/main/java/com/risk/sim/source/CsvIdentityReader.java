package com.risk.sim.source;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Reader for train_identity.csv from IEEE-CIS dataset.
 */
@Slf4j
@Component
public class CsvIdentityReader {

    private final Map<String, TransactionRecord> identityRecords = new HashMap<>();

    /**
     * Read identity CSV file and parse records.
     *
     * @param filePath Path to the CSV file
     * @return Map of TransactionID -> TransactionRecord (with identity fields only)
     */
    public Map<String, TransactionRecord> readIdentities(String filePath) throws IOException, CsvValidationException {
        log.info("Reading identity CSV from: {}", filePath);

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] header = reader.readNext();
            if (header == null) {
                log.error("Empty CSV file");
                return identityRecords;
            }

            // Create header index map for efficient lookup
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                headerIndex.put(header[i], i);
            }

            String[] row;
            int count = 0;
            // TODO: 临时限制读取 100 行用于测试，生产环境需移除此限制
            int maxRows = 100;
            while ((row = reader.readNext()) != null && count < maxRows) {
                try {
                    TransactionRecord record = parseIdentityRow(row, headerIndex);
                    if (record != null && record.getTransactionId() != null) {
                        identityRecords.put(record.getTransactionId(), record);
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse identity row {}: {}", count, e.getMessage());
                }
            }

            log.info("Successfully loaded {} identity records", count);
        }

        return identityRecords;
    }

    private TransactionRecord parseIdentityRow(String[] row, Map<String, Integer> headerIndex) {
        TransactionRecord record = new TransactionRecord();

        record.setTransactionId(getField(row, headerIndex, "TransactionID"));

        // ID fields
        for (int i = 1; i <= 38; i++) {
            String fieldName = String.format("id-%02d", i);
            String value = getField(row, headerIndex, fieldName);
            setIdField(record, i, value);
        }

        // Device
        record.setDeviceType(parseInteger(getField(row, headerIndex, "DeviceType")));
        record.setDeviceInfo(getField(row, headerIndex, "DeviceInfo"));

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

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setIdField(TransactionRecord record, int idNum, String value) {
        // This is a simplified version
        // You would use reflection or explicit switch statement to set the field
        // For now, we're just storing the transactionId
    }

    public int getRecordCount() {
        return identityRecords.size();
    }

    public Collection<TransactionRecord> getAllRecords() {
        return identityRecords.values();
    }
}
