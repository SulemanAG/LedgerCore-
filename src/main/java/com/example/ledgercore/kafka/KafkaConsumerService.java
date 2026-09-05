package com.example.ledgercore.kafka;

import com.example.ledgercore.event.EventProcessingService;
import com.example.ledgercore.event.KafkaEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes LedgerCore events from Kafka.
 * This consumes the event only once.
 * @author Suleman Agasimani
 */
@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final EventProcessingService eventProcessingService;

    public KafkaConsumerService(
            ObjectMapper objectMapper,
            EventProcessingService eventProcessingService
    ) {
        this.objectMapper = objectMapper;
        this.eventProcessingService = eventProcessingService;
    }

    /**
     * Consumes an event from the LedgerCore Kafka topic.
     *
     * @param message serialized Kafka event
     * @throws Exception if event deserialization or processing fails
     */
    @KafkaListener(
            topics = "ledgercore-transactions",
            groupId = "ledgercore-consumer"
    )
    public void consume(String message) throws Exception {

        // 1. Deserialize the Kafka event envelope.
        KafkaEvent event =
                objectMapper.readValue(message, KafkaEvent.class);

        // 2. Process the event through the idempotency layer.
        eventProcessingService.process(event);
    }
}