package com.example.ledgercore.event;

import com.example.ledgercore.repository.ProcessedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for concurrent Kafka event processing.
 *
 * <p>
 * Verifies that concurrent deliveries of the same Kafka event
 * result in exactly one processed-event record.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class ConcurrentKafkaConsumerIdempotencyTest {

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
     * Removes the event created by the test.
     */
    @AfterEach
    void tearDown() {

        // 1. Delete the test event.
        processedEventRepository
                .findByEventId(eventId)
                .ifPresent(processedEventRepository::delete);
    }

    /**
     * Verifies that two concurrent deliveries of the same event
     * result in exactly one processed-event record.
     */
    @Test
    void shouldProcessConcurrentDuplicateEventOnlyOnce()
            throws Exception {

        // 1. Create one Kafka event shared by both threads.
        KafkaEvent event = new KafkaEvent(
                eventId,
                "TRANSFER_COMPLETED",
                999L,
                LocalDateTime.now(),
                "{\"transactionId\":999,\"amount\":1000.00}"
        );

        // 2. Create two worker threads.
        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        // 3. Synchronize both threads before processing.
        CountDownLatch startLatch =
                new CountDownLatch(1);

        Future<?> firstWorker =
                executorService.submit(() -> {

                    try {
                        startLatch.await();

                        eventProcessingService.process(event);

                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                });

        Future<?> secondWorker =
                executorService.submit(() -> {

                    try {
                        startLatch.await();

                        eventProcessingService.process(event);

                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                });

        // 4. Release both workers at approximately the same time.
        startLatch.countDown();

        // 5. Wait for both workers to finish.
        firstWorker.get();
        secondWorker.get();

        // 6. Shut down the executor.
        executorService.shutdown();

        // 7. Verify that the processed event exists.
        assertTrue(
                processedEventRepository
                        .findByEventId(eventId)
                        .isPresent(),
                "Processed event should exist"
        );

        // 8. Verify that exactly one record exists.
        long count =
                processedEventRepository
                        .findByEventId(eventId)
                        .stream()
                        .count();

        assertEquals(
                1,
                count,
                "Concurrent duplicate event must be recorded only once"
        );
    }
}