package com.risk.sim.source;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Joins transaction and identity records by TransactionID.
 */
@Slf4j
@Component
public class Joiner {

    /**
     * Join transaction records with identity records by TransactionID.
     *
     * @param transactions Map of TransactionID -> TransactionRecord (from transaction.csv)
     * @param identities   Map of TransactionID -> TransactionRecord (from identity.csv)
     * @return List of joined TransactionRecord objects
     */
    public List<TransactionRecord> join(
            Map<String, TransactionRecord> transactions,
            Map<String, TransactionRecord> identities) {

        log.info("Joining {} transaction records with {} identity records",
                transactions.size(), identities.size());

        List<TransactionRecord> joinedRecords = new ArrayList<>();

        for (Map.Entry<String, TransactionRecord> entry : transactions.entrySet()) {
            String transactionId = entry.getKey();
            TransactionRecord txRecord = entry.getValue();

            TransactionRecord identityRecord = identities.get(transactionId);

            // Copy identity fields into transaction record
            if (identityRecord != null) {
                copyIdentityFields(txRecord, identityRecord);
            } else {
                log.trace("No identity record found for transaction ID: {}", transactionId);
            }

            joinedRecords.add(txRecord);
        }

        log.info("Joined {} records successfully", joinedRecords.size());
        return joinedRecords;
    }

    /**
     * Join transaction records with identity records and filter by criteria.
     *
     * @param transactions Map of TransactionID -> TransactionRecord (from transaction.csv)
     * @param identities   Map of TransactionID -> TransactionRecord (from identity.csv)
     * @param fraudOnly    If true, only include fraud transactions
     * @param productCds   If not empty, only include transactions with these ProductCDs
     * @return List of joined and filtered TransactionRecord objects
     */
    public List<TransactionRecord> joinAndFilter(
            Map<String, TransactionRecord> transactions,
            Map<String, TransactionRecord> identities,
            boolean fraudOnly,
            List<String> productCds) {

        List<TransactionRecord> joinedRecords = join(transactions, identities);

        return joinedRecords.stream()
                .filter(record -> {
                    // Filter by fraud status
                    if (fraudOnly && !record.isFraud()) {
                        return false;
                    }

                    // Filter by ProductCD
                    if (!productCds.isEmpty() && record.getProductCd() != null) {
                        if (!productCds.contains(record.getProductCd())) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    private void copyIdentityFields(TransactionRecord target, TransactionRecord source) {
        // Copy ID fields
        target.setId01(source.getId01());
        target.setId02(source.getId02());
        target.setId03(source.getId03());
        target.setId04(source.getId04());
        target.setId05(source.getId05());
        target.setId06(source.getId06());
        target.setId07(source.getId07());
        target.setId08(source.getId08());
        target.setId09(source.getId09());
        target.setId10(source.getId10());
        target.setId11(source.getId11());

        // Copy device info
        target.setDeviceType(source.getDeviceType());
        target.setDeviceInfo(source.getDeviceInfo());

        // Copy other ID fields
        target.setId12(source.getId12());
        target.setId13(source.getId13());
        target.setId14(source.getId14());
        target.setId15(source.getId15());
        target.setId16(source.getId16());
        target.setId17(source.getId17());
        target.setId18(source.getId18());
        target.setId19(source.getId19());
        target.setId20(source.getId20());
        target.setId21(source.getId21());
        target.setId22(source.getId22());
        target.setId23(source.getId23());
        target.setId24(source.getId24());
        target.setId25(source.getId25());
        target.setId26(source.getId26());
        target.setId27(source.getId27());
        target.setId28(source.getId28());
        target.setId29(source.getId29());
        target.setId30(source.getId30());
        target.setId31(source.getId31());
        target.setId32(source.getId32());
        target.setId33(source.getId33());
        target.setId34(source.getId34());
        target.setId35(source.getId35());
        target.setId36(source.getId36());
        target.setId37(source.getId37());
        target.setId38(source.getId38());
    }

    /**
     * Calculate join statistics.
     *
     * @param transactions Map of transaction records
     * @param identities   Map of identity records
     * @return Statistics string
     */
    public String getJoinStatistics(
            Map<String, TransactionRecord> transactions,
            Map<String, TransactionRecord> identities) {

        int txCount = transactions.size();
        int idCount = identities.size();
        int joinCount = 0;

        for (String txId : transactions.keySet()) {
            if (identities.containsKey(txId)) {
                joinCount++;
            }
        }

        double joinRate = txCount > 0 ? (double) joinCount / txCount * 100.0 : 0.0;

        return String.format("Join Statistics: Transactions=%d, Identities=%d, Joined=%d (%.2f%%)",
                txCount, idCount, joinCount, joinRate);
    }
}
