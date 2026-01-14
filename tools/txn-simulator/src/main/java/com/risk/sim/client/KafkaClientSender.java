package com.risk.sim.client;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.risk.sim.source.TransactionRecord;;

@Slf4j
@Component
public class KafkaClientSender {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;

    public KafkaClientSender(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    public void send(String topic, TransactionRecord record) throws Exception {
        try {
            String key = record.getTransactionId();
            String jsonMessage = mapper.writeValueAsString(record);
            kafkaTemplate.send(topic, key, jsonMessage);
            log.info("Sent message to topic {}: {} -{}", topic, key, jsonMessage);
        } catch (Exception e) {
            log.error("Failed to send message to topic {}: {}", topic, e.getMessage());
            throw e;
        }
    }
}
