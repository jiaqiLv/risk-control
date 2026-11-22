package com.risk.txn.writer;

import com.risk.data.entity.TransactionAttributeEntity;
import com.risk.data.entity.TransactionEntity;
import com.risk.data.entity.TransactionIdentityEntity;
import com.risk.data.repository.TransactionAttributeRepository;
import com.risk.data.repository.TransactionIdentityRepository;
import com.risk.data.repository.TransactionRepository;
import com.risk.txn.config.TransactionProperties;
import com.risk.txn.event.TransactionEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Batch writer for transaction data.
 * Accumulates events and writes them to database in batches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTransactionWriter {

    private final TransactionRepository transactionRepository;
    private final TransactionAttributeRepository attributeRepository;
    private final TransactionIdentityRepository identityRepository;
    private final TransactionProperties properties;

    private final CopyOnWriteArrayList<TransactionEvent> buffer = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        // Schedule periodic flush based on configuration
        scheduler.scheduleAtFixedRate(
            this::flush,
            properties.getBatch().getLingerMs(),
            properties.getBatch().getLingerMs(),
            TimeUnit.MILLISECONDS
        );
        log.info("BatchTransactionWriter initialized with batch size: {}, linger: {}ms",
            properties.getBatch().getSize(),
            properties.getBatch().getLingerMs());
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        // Flush remaining data on shutdown
        flush();
    }

    /**
     * Add events to batch buffer.
     */
    public void addEvents(List<TransactionEvent> events) {
        buffer.addAll(events);
        log.debug("Added {} events to buffer, current size: {}", events.size(), buffer.size());

        // Flush immediately if batch size is reached
        if (buffer.size() >= properties.getBatch().getSize()) {
            flush();
        }
    }

    /**
     * Add single event to batch buffer.
     */
    public void addEvent(TransactionEvent event) {
        buffer.add(event);
        log.debug("Added event to buffer, current size: {}", buffer.size());

        // Flush immediately if batch size is reached
        if (buffer.size() >= properties.getBatch().getSize()) {
            flush();
        }
    }

    /**
     * Flush buffer to database.
     * This method is synchronized to prevent concurrent flushes.
     */
    @Transactional
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        int batchSize = buffer.size();
        long startTime = System.currentTimeMillis();

        try {
            // Convert events to entities
            List<TransactionEntity> entities = convertToEntities(new ArrayList<>(buffer));

            // Batch save
            List<TransactionEntity> savedEntities = transactionRepository.saveAll(entities);

            // Save attributes and identities
            for (int i = 0; i < savedEntities.size(); i++) {
                TransactionEntity entity = savedEntities.get(i);
                TransactionEvent event = buffer.get(i);

                // Save attributes
                if (event.getPayload().getAttributes() != null) {
                    TransactionAttributeEntity attrEntity = convertToAttributeEntity(
                        event, entity.getId());
                    attributeRepository.save(attrEntity);
                }

                // Save identity
                if (event.getPayload().getIdentity() != null) {
                    TransactionIdentityEntity identityEntity = convertToIdentityEntity(
                        event, entity.getId());
                    identityRepository.save(identityEntity);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch write completed: {} records in {}ms ({} TPS)",
                batchSize, duration,
                (batchSize * 1000L) / Math.max(duration, 1));

            // Clear buffer after successful write
            buffer.clear();

        } catch (Exception e) {
            log.error("Batch write failed: {} records, error: {}", batchSize, e.getMessage(), e);
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Batch write failed", e);
        }
    }

    private List<TransactionEntity> convertToEntities(List<TransactionEvent> events) {
        List<TransactionEntity> entities = new ArrayList<>();

        for (TransactionEvent event : events) {
            TransactionEvent.TransactionPayload payload = event.getPayload();

            TransactionEntity entity = TransactionEntity.builder()
                .transactionId(payload.getTransactionId())
                .userId(payload.getUserId())
                .merchantId(payload.getMerchantId() != null ? payload.getMerchantId() : "default")
                .eventTimestamp(payload.getEventTimestamp())
                .amount(payload.getAmount())
                .productCd(payload.getProductCd())
                .decision(payload.getDecision() != null ? payload.getDecision() : "PENDING")
                .riskScore(payload.getRiskScore() != null ? payload.getRiskScore() : 0.0)
                .isFraud(payload.getIsFraud() != null ? payload.getIsFraud() : false)
                .build();

            entities.add(entity);
        }

        return entities;
    }

    private TransactionAttributeEntity convertToAttributeEntity(
            TransactionEvent event, Long transactionId) {

        Map<String, Object> attrs = event.getPayload().getAttributes();

        return TransactionAttributeEntity.builder()
            .transactionId(transactionId)
            .card1(attrs != null ? getValueAsDouble(attrs, "card1") : null)
            .card2(attrs != null ? getValueAsDouble(attrs, "card2") : null)
            .card3(attrs != null ? getValueAsDouble(attrs, "card3") : null)
            .card4(attrs != null ? getValueAsString(attrs, "card4") : null)
            .card5(attrs != null ? getValueAsDouble(attrs, "card5") : null)
            .card6(attrs != null ? getValueAsString(attrs, "card6") : null)
            .addr1(attrs != null ? getValueAsDouble(attrs, "addr1") : null)
            .addr2(attrs != null ? getValueAsDouble(attrs, "addr2") : null)
            .dist1(attrs != null ? getValueAsDouble(attrs, "dist1") : null)
            .dist2(attrs != null ? getValueAsString(attrs, "dist2") : null)
            .build();
    }

    private TransactionIdentityEntity convertToIdentityEntity(
            TransactionEvent event, Long transactionId) {

        Map<String, Object> identity = event.getPayload().getIdentity();

        return TransactionIdentityEntity.builder()
            .transactionId(transactionId)
            .deviceType(identity != null ? getValueAsString(identity, "deviceType") : null)
            .deviceInfo(identity != null ? getValueAsString(identity, "deviceInfo") : null)
            .build();
    }

    private Double getValueAsDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getValueAsString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    public int getBufferSize() {
        return buffer.size();
    }
}
