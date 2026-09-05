package com.example.ledgercore.kafka;

import com.example.ledgercore.event.EventProcessingService;
import com.example.ledgercore.event.KafkaEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes LedgerCore events from Kafka.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final EventProcessingService eventProcessingService;

    /**
     * Creates the Kafka consumer service.
     *
     * @param objectMapper Jackson object mapper
     * @param eventProcessingService service responsible for
     *                              event processing
     */
    public KafkaConsumerService(
            ObjectMapper objectMapper,
            EventProcessingService eventProcessingService
    ) {
        this.objectMapper = objectMapper;
        this.eventProcessingService = eventProcessingService;
    }

    /**
     * Consumes LedgerCore events from Kafka.
     *
     * @param message serialized Kafka event
     * @throws Exception when event processing fails
     */
    @KafkaListener(
            topics = "ledgercore-transactions",
            groupId = "ledgercore-consumer"
    )
    public void consume(String message) throws Exception {

        // 1. Deserialize the Kafka event.
        KafkaEvent event =
                objectMapper.readValue(
                        message,
                        KafkaEvent.class
                );

        // 2. Deliberately fail TEST_FAILURE events.
        if ("TEST_FAILURE".equals(event.eventType())) {

            throw new RuntimeException(
                    "Intentional Kafka consumer failure for DLT testing"
            );
        }

        // 3. Process normal events.
        eventProcessingService.process(event);
    }
}