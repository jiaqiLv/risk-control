package com.risk.txn.consumer;

import com.risk.txn.event.TransactionEvent;
import com.risk.txn.monitor.DataCollectionMonitor;
import com.risk.txn.publisher.DeadLetterQueuePublisher;
import com.risk.txn.writer.BatchTransactionWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer for transaction events.
 * Consumes events from Kafka and writes them to database in batches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final BatchTransactionWriter batchWriter;
    private final DataCollectionMonitor monitor;
    private final DeadLetterQueuePublisher dlqPublisher;

    /**
     * Consume transaction events from Kafka.
     *
     * @param events List of transaction events
     * @param ack Acknowledgment object for manual commit
     */
    @KafkaListener(
        topics = "${transaction.data-collection.topic:tx.events}",
        groupId = "${spring.kafka.consumer.group-id:transaction-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionEvents(
            @Payload List<TransactionEvent> events,
            Acknowledgment ack) {

        if (events.isEmpty()) {
            return;
        }

        int eventCount = events.size();
        log.debug("Received {} events from Kafka", eventCount);

        try {
            // Add events to batch writer
            batchWriter.addEvents(events);

            // Commit offset after successful processing
            if (ack != null) {
                ack.acknowledge();
            }

            // Record success metrics
            monitor.recordSuccess(eventCount);

            log.debug("Successfully processed {} events", eventCount);

        } catch (Exception e) {
            log.error("Error processing transaction events: count={}, error={}",
                eventCount, e.getMessage(), e);

            // Record failure metrics
            monitor.recordFailure(eventCount, e);

            // Send to dead letter queue
            try {
                dlqPublisher.publishToDlq(events, e.getMessage());
            } catch (Exception dlqEx) {
                log.error("Failed to send events to DLQ: {}", dlqEx.getMessage());
            }

            // Still acknowledge to move past the bad message
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }
}
