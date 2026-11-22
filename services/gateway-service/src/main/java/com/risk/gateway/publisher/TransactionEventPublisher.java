package com.risk.gateway.publisher;

import com.risk.gateway.event.TransactionEvent;
import com.risk.gateway.interceptor.RequestSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publisher for transaction events to Kafka.
 * Sends events asynchronously and does not block the main request processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${gateway.data-collection.topic:tx.events}")
    private String topic;

    @Value("${gateway.data-collection.enabled:true}")
    private boolean enabled;

    /**
     * Publish transaction event to Kafka asynchronously.
     * This is a fire-and-forget operation - failures are logged but don't affect the main flow.
     *
     * @param event Transaction event to publish
     */
    public void publishTransaction(TransactionEvent event) {
        if (!enabled) {
            log.debug("Data collection is disabled, skipping event publish");
            return;
        }

        try {
            // Generate event ID if not present
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            // Async send - don't wait for confirmation
            kafkaTemplate.send(topic, event.getTransactionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug(
                            "Event published successfully: transactionId={}, partition={}, offset={}",
                            event.getTransactionId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                        );
                    } else {
                        log.error(
                            "Failed to publish event to Kafka: transactionId={}, error={}",
                            event.getTransactionId(),
                            ex.getMessage()
                        );
                    }
                });

        } catch (Exception e) {
            // Log but don't throw - we don't want to affect the main request flow
            log.error("Error publishing transaction event: transactionId={}, error={}",
                event.getTransactionId(), e.getMessage(), e);
        }
    }

    /**
     * Create and publish a transaction event from request and response data.
     *
     * @param transactionId Transaction ID
     * @param requestSnapshot Request snapshot
     * @param response Response object with decision and risk score
     * @param responseTime Response time in milliseconds
     */
    public void publishTransactionFromRequestResponse(
            String transactionId,
            RequestSnapshot requestSnapshot,
            Object response,
            long responseTime) {

        TransactionEvent.TransactionPayload payload = buildPayload(requestSnapshot, response);
        TransactionEvent.EventMetadata metadata = buildMetadata(requestSnapshot, responseTime);

        TransactionEvent event = TransactionEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .transactionId(transactionId)
            .eventType("TRANSACTION_CREATED")
            .timestamp(System.currentTimeMillis())
            .payload(payload)
            .metadata(metadata)
            .build();

        publishTransaction(event);
    }

    private TransactionEvent.TransactionPayload buildPayload(
            RequestSnapshot snapshot,
            Object response) {
        // Extract decision and risk score from response
        String decision = "PENDING";
        Double riskScore = 0.0;

        // TODO: Extract from response object based on actual response structure
        // For now, use placeholder values

        return TransactionEvent.TransactionPayload.builder()
            .transactionId(snapshot.getTransactionId())
            .userId(snapshot.getUserId())
            .merchantId("default") // TODO: extract if available
            .eventTimestamp(snapshot.getRequestTimestamp())
            .amount(snapshot.getAmount())
            .productCd(snapshot.getProductCd())
            .decision(decision)
            .riskScore(riskScore)
            .isFraud(false) // Default, will be updated by labels
            .attributes(snapshot.getAttributes())
            .identity(snapshot.getIdentity())
            .build();
    }

    private TransactionEvent.EventMetadata buildMetadata(
            RequestSnapshot snapshot,
            long responseTime) {
        return TransactionEvent.EventMetadata.builder()
            .gatewayTimestamp(snapshot.getRequestTimestamp())
            .responseTime(responseTime)
            .sourceIp(snapshot.getSourceIp())
            .userAgent(snapshot.getUserAgent())
            .build();
    }
}
