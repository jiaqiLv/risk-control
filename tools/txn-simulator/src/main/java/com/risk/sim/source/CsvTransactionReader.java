package com.risk.sim.source;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/**
 * Reader for train_transaction.csv from IEEE-CIS dataset.
 */
@Slf4j
@Component
public class CsvTransactionReader {

    private final Map<String, TransactionRecord> records = new HashMap<>();

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
                headerIndex.put(header[i], i);
            }

            String[] row;
            int count = 0;
            // TODO: 临时限制读取 100 行用于测试，生产环境需移除此限制
            int maxRows = 100;
            log.info("Header index: {}", headerIndex);
            while ((row = reader.readNext()) != null && count < maxRows) {
                try {
                    log.info("Reading row {}: {}", count, Arrays.toString(row));
                    TransactionRecord record = parseTransactionRow(row, headerIndex);
                    log.info("Parsed transaction record: {}", record);
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
        record.setTransactionDt(parseInteger(getField(row, headerIndex, "TransactionDT")));
        record.setTransactionAmt(parseBigDecimal(getField(row, headerIndex, "TransactionAmt")));
        record.setProductCd(getString(getField(row, headerIndex, "ProductCD")));

        // Email domain fields
        record.setPEmailDomain(getField(row, headerIndex, "P_emaildomain"));
        record.setREmailDomain(getField(row, headerIndex, "R_emaildomain"));

        // Card fields
        record.setCard1(parseInteger(getField(row, headerIndex, "card1")));
        record.setCard2(parseInteger(getField(row, headerIndex, "card2")));
        record.setCard3(parseInteger(getField(row, headerIndex, "card3")));
        record.setCard4(getString(getField(row, headerIndex, "card4")));
        record.setCard5(parseInteger(getField(row, headerIndex, "card5")));
        record.setCard6(parseInteger(getField(row, headerIndex, "card6")));

        // Address fields
        record.setAddr1(parseInteger(getField(row, headerIndex, "addr1")));
        record.setAddr2(parseInteger(getField(row, headerIndex, "addr2")));

        // Distance fields
        record.setDist1(parseDouble(getField(row, headerIndex, "dist1")));
        record.setDist2(parseDouble(getField(row, headerIndex, "dist2")));

        // C fields (count of something)
        for (int i = 1; i <= 14; i++) {
            setIntField(record, "c" + i, parseInteger(getField(row, headerIndex, "C" + i)));
        }

        // D fields (date/time delta)
        for (int i = 1; i <= 15; i++) {
            setDoubleField(record, "d" + i, parseDouble(getField(row, headerIndex, "D" + i)));
        }

        // M fields (match) - String values like "T", "F", "M2"
        for (int i = 1; i <= 9; i++) {
            setStringField(record, "m" + i, getString(getField(row, headerIndex, "M" + i)));
        }

        // V fields (Vesta engineered features)
        for (int i = 1; i <= 339; i++) {
            setIntField(record, "v" + i, parseInteger(getField(row, headerIndex, "V" + i)));
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

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

    private void setIntField(TransactionRecord record, String fieldName, Integer value) {
        try {
            Field field = TransactionRecord.FIELD_CACHE.get(fieldName.toLowerCase());
            if (field != null) {
                field.set(record, value);
            }
        } catch (IllegalAccessException e) {
            log.warn("Failed to set int field {}: {}", fieldName, e.getMessage());
        }
    }

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
}
