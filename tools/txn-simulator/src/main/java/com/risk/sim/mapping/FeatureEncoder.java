package com.risk.sim.mapping;

import com.risk.sim.source.TransactionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Encodes features from TransactionRecord.
 * This class provides methods to normalize and encode features for model inference.
 */
@Slf4j
@Component
public class FeatureEncoder {

    /**
     * Encode numeric features with null handling and normalization.
     *
     * @param record Transaction record
     * @return Map of feature name -> encoded value
     */
    public Map<String, Double> encodeNumericFeatures(TransactionRecord record) {
        Map<String, Double> features = new HashMap<>();

        // Transaction amount (log transform)
        if (record.getTransactionAmt() != null) {
            double logAmt = Math.log1p(record.getTransactionAmt().doubleValue());
            features.put("TransactionAmt_log", logAmt);
        }

        // TransactionDT (normalized to hours)
        if (record.getTransactionDt() != null) {
            double dtHours = (double)(record.getTransactionDt()) / 3600.0;
            features.put("TransactionDT_hours", dtHours);
        }

        // Card fields (categorical, just indicator)
        if (record.getCard1() != null) {
            features.put("card1", record.getCard1().doubleValue());
        }
        if (record.getCard2() != null) {
            features.put("card2", record.getCard2().doubleValue());
        }

        // Address fields
        if (record.getAddr1() != null) {
            features.put("addr1", record.getAddr1().doubleValue());
        }
        if (record.getAddr2() != null) {
            features.put("addr2", record.getAddr2().doubleValue());
        }

        // Distance fields
        if (record.getDist1() != null) {
            features.put("dist1", record.getDist1());
        }
        if (record.getDist2() != null) {
            features.put("dist2", record.getDist2());
        }

        // C1-C14 (count features)
        for (int i = 1; i <= 14; i++) {
            Integer value = getCValue(record, i);
            if (value != null) {
                features.put("C" + i, value.doubleValue());
            }
        }

        // D1-D15 (date delta features)
        for (int i = 1; i <= 15; i++) {
            Double value = getDValue(record, i);
            if (value != null) {
                features.put("D" + i, value);
            }
        }

        return features;
    }

    /**
     * Encode categorical features as one-hot or label encoding.
     *
     * @param record Transaction record
     * @return Map of feature name -> encoded string value
     */
    public Map<String, String> encodeCategoricalFeatures(TransactionRecord record) {
        Map<String, String> features = new HashMap<>();

        // ProductCD (now String type like "W", "H", "C", "S", "R")
        if (record.getProductCd() != null) {
            features.put("ProductCD", record.getProductCd());
        }

        // Card4 (card provider, now String type like "discover", "visa", "mastercard")
        if (record.getCard4() != null) {
            features.put("card4", record.getCard4());
        }

        // Card6 (card type, still Integer)
        if (record.getCard6() != null) {
            features.put("card6", record.getCard6().toString());
        }

        // Email domain (id-01)
        if (record.getId01() != null) {
            features.put("email_domain", record.getId01());
        }

        // Device info
        if (record.getDeviceInfo() != null) {
            features.put("device_info", record.getDeviceInfo());
        }

        // Device type
        if (record.getDeviceType() != null) {
            features.put("device_type", record.getDeviceType().toString());
        }

        // M1-M9 (match features, now String type like "T", "F", "M2")
        for (int i = 1; i <= 9; i++) {
            String value = getMValue(record, i);
            if (value != null) {
                features.put("M" + i, value);
            }
        }

        return features;
    }

    /**
     * Check if a feature is missing.
     *
     * @param record Transaction record
     * @param featureName Name of the feature
     * @return true if missing, false otherwise
     */
    public boolean isMissing(TransactionRecord record, String featureName) {
        return switch (featureName) {
            case "dist1" -> record.getDist1() == null;
            case "dist2" -> record.getDist2() == null;
            case "D1" -> getDValue(record, 1) == null;
            case "D2" -> getDValue(record, 2) == null;
            case "DeviceInfo" -> record.getDeviceInfo() == null;
            case "id-01" -> record.getId01() == null;
            default -> false;
        };
    }

    /**
     * Count missing features in a record.
     *
     * @param record Transaction record
     * @return Number of missing features
     */
    public int countMissing(TransactionRecord record) {
        int count = 0;

        if (record.getDist1() == null) count++;
        if (record.getDist2() == null) count++;
        if (record.getDeviceInfo() == null) count++;
        if (record.getId01() == null) count++;

        for (int i = 1; i <= 15; i++) {
            if (getDValue(record, i) == null) count++;
        }

        for (int i = 1; i <= 9; i++) {
            if (getMValue(record, i) == null) count++;
        }

        return count;
    }

    // Helper methods to get field values dynamically
    private Integer getCValue(TransactionRecord record, int index) {
        // Use reflection to get C field value (c1-c14 are Integer type)
        try {
            String fieldName = "c" + index;
            var field = com.risk.sim.source.TransactionRecord.FIELD_CACHE.get(fieldName);
            if (field != null) {
                Object value = field.get(record);
                return value != null ? (Integer) value : null;
            }
        } catch (Exception e) {
            log.trace("Failed to get C{} value: {}", index, e.getMessage());
        }
        return null;
    }

    private Double getDValue(TransactionRecord record, int index) {
        // Use reflection to get D field value (d1-d15 are Double type)
        try {
            String fieldName = "d" + index;
            var field = com.risk.sim.source.TransactionRecord.FIELD_CACHE.get(fieldName);
            if (field != null) {
                Object value = field.get(record);
                return value != null ? (Double) value : null;
            }
        } catch (Exception e) {
            log.trace("Failed to get D{} value: {}", index, e.getMessage());
        }
        return null;
    }

    private String getMValue(TransactionRecord record, int index) {
        // Use reflection to get M field value (m1-m9 are now String type)
        try {
            String fieldName = "m" + index;
            var field = com.risk.sim.source.TransactionRecord.FIELD_CACHE.get(fieldName);
            if (field != null) {
                Object value = field.get(record);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.trace("Failed to get M{} value: {}", index, e.getMessage());
        }
        return null;
    }

    /**
     * Normalize a numeric feature using min-max normalization.
     *
     * @param value Feature value
     * @param min   Minimum value for the feature
     * @param max   Maximum value for the feature
     * @return Normalized value between 0 and 1
     */
    public double normalize(double value, double min, double max) {
        if (max == min) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    /**
     * Standardize a numeric feature using z-score normalization.
     *
     * @param value Feature value
     * @param mean  Mean value for the feature
     * @param std   Standard deviation for the feature
     * @return Standardized value
     */
    public double standardize(double value, double mean, double std) {
        if (std == 0) {
            return 0.0;
        }
        return (value - mean) / std;
    }
}
