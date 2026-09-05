package com.example.ledgercore.event;

import com.example.ledgercore.repository.ProcessedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka consumer idempotency.
 *
 * <p>
 * These tests verify that LedgerCore processes a Kafka event once
 * and ignores subsequent deliveries of the same event.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class KafkaConsumerIdempotencyTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    private Long eventId;

    /**
     * Creates a unique event ID before every test.
     */
    @BeforeEach
    void setUp() {

        // 1. Generate a unique event ID.
        eventId = Math.abs(
                UUID.randomUUID().getMostSignificantBits()
        );
    }

    /**
     * Removes the processed event created by the test.
     */
    @AfterEach
    void tearDown() {

        // 1. Remove the test event from the database.
        processedEventRepository
                .findByEventId(eventId)
                .ifPresent(processedEventRepository::delete);
    }

    /**
     * Verifies that a new Kafka event is processed and
     * recorded in the processed_events table.
     */
    @Test
    void shouldProcessNewEvent() {

        // 1. Create a Kafka event.
        KafkaEvent event = new KafkaEvent(
                eventId,
                "TRANSFER_COMPLETED",
                999L,
                LocalDateTime.now(),
                "{\"transactionId\":999,\"amount\":1000.00}"
        );

        // 2. Process the event.
        eventProcessingService.process(event);

        // 3. Verify that the event was recorded.
        var processedEvent =
                processedEventRepository
                        .findByEventId(eventId);

        assertTrue(
                processedEvent.isPresent(),
                "Processed event should exist in the database"
        );

        // 4. Verify the event metadata.
        assertEquals(
                eventId,
                processedEvent.get().getEventId()
        );

        assertEquals(
                "TRANSFER_COMPLETED",
                processedEvent.get().getEventType()
        );
    }

    /**
     * Verifies that processing the same Kafka event twice
     * does not create duplicate processing records.
     */
    @Test
    void shouldIgnoreDuplicateEvent() {

        // 1. Create a Kafka event.
        KafkaEvent event = new KafkaEvent(
                eventId,
                "TRANSFER_COMPLETED",
                999L,
                LocalDateTime.now(),
                "{\"transactionId\":999,\"amount\":1000.00}"
        );

        // 2. Process the event for the first time.
        eventProcessingService.process(event);

        // 3. Process the exact same event again.
        eventProcessingService.process(event);

        // 4. Verify that only one record exists.
        long count =
                processedEventRepository
                        .findByEventId(eventId)
                        .stream()
                        .count();

        assertEquals(
                1,
                count,
                "Duplicate event must not create another processed-event record"
        );
    }
}