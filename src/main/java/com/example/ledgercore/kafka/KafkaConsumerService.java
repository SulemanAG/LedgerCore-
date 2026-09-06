package com.example.ledgercore.kafka;

import com.example.ledgercore.event.EventProcessingService;
import com.example.ledgercore.event.KafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumes LedgerCore events from Kafka.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final ObjectMapper objectMapper;
    private final EventProcessingService eventProcessingService;

    // In-memory counter for testing transient failures during automated integration testing
    private final ConcurrentHashMap<Long, AtomicInteger> transientAttemptCounts = new ConcurrentHashMap<>();

    /**
     * Creates the Kafka consumer service.
     *
     * @param objectMapper Jackson object mapper
     * @param eventProcessingService service responsible for event processing
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
            groupId = "${spring.kafka.consumer.group-id:ledgercore-consumer}"
    )
    public void consume(String message) throws Exception {
        log.info("KAFKA CONSUMER RECEIVED MESSAGE: {}", message);

        // 1. Deserialize the Kafka event.
        KafkaEvent event =
                objectMapper.readValue(
                        message,
                        KafkaEvent.class
                );

        // 2. Deliberately fail TEST_FAILURE events permanently for DLT testing.
        if ("TEST_FAILURE".equals(event.eventType())) {

            throw new RuntimeException(
                    "Intentional Kafka consumer failure for DLT testing"
            );
        }

        // 3. Fail TRANSIENT_FAILURE events on attempt 1, succeed on attempt 2 (processed as DEPOSIT_COMPLETED).
        if ("TRANSIENT_FAILURE".equals(event.eventType())) {

            AtomicInteger attemptCounter = transientAttemptCounts.computeIfAbsent(
                    event.eventId(),
                    id -> new AtomicInteger(0)
            );

            int currentAttempt = attemptCounter.incrementAndGet();

            if (currentAttempt == 1) {

                throw new RuntimeException(
                        "Intentional transient consumer failure on attempt 1 for event " + event.eventId()
                );
            }

            // On attempt 2, process payload as DEPOSIT_COMPLETED
            KafkaEvent depositEvent = new KafkaEvent(
                    event.eventId(),
                    "DEPOSIT_COMPLETED",
                    event.aggregateId(),
                    event.occurredAt(),
                    event.payload()
            );
            eventProcessingService.process(depositEvent);
            return;
        }

        // 4. Process normal events.
        eventProcessingService.process(event);
    }
}