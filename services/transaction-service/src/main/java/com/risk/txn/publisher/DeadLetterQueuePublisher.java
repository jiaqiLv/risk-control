package com.risk.txn.publisher;

import com.risk.txn.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publisher for dead letter queue.
 * Sends failed events to DLQ for later inspection and recovery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterQueuePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${transaction.data-collection.dlq-topic:tx.dlq}")
    private String dlqTopic;

    /**
     * Publish failed events to dead letter queue.
     *
     * @param events Failed events
     * @param errorMessage Error message
     */
    public void publishToDlq(List<TransactionEvent> events, String errorMessage) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Create DLQ message with metadata
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("events", events);
            dlqMessage.put("errorMessage", errorMessage);
            dlqMessage.put("timestamp", Instant.now().toString());
            dlqMessage.put("eventCount", events.size());

            // Send to DLQ topic
            kafkaTemplate.send(dlqTopic, dlqMessage)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info(
                            "Sent {} events to DLQ: partition={}, offset={}",
                            events.size(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                        );
                    } else {
                        log.error(
                            "Failed to send events to DLQ: count={}, error={}",
                            events.size(), ex.getMessage()
                        );
                    }
                });

        } catch (Exception e) {
            log.error("Error publishing to DLQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish single failed event to dead letter queue.
     *
     * @param event Failed event
     * @param errorMessage Error message
     */
    public void publishToDlq(TransactionEvent event, String errorMessage) {
        publishToDlq(List.of(event), errorMessage);
    }
}
