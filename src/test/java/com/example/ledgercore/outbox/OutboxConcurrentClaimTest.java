package com.example.ledgercore.outbox;

import com.example.ledgercore.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for concurrent transactional outbox claiming.
 *
 * <p>
 * These tests verify that multiple relay workers can compete for
 * pending outbox events without claiming the same event twice.
 * PostgreSQL row-level locking and SKIP LOCKED provide the
 * concurrency control.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class OutboxConcurrentClaimTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPreventTwoWorkersFromClaimingSameEvent() throws Exception {

        // 1. Create a controlled batch of pending outbox events
        int eventCount = 20;

        List<Long> eventIds = new ArrayList<>();

        for (int i = 0; i < eventCount; i++) {

            OutboxEvent event = new OutboxEvent(
                    "CONCURRENT_CLAIM_TEST",
                    900000L + i,
                    "{\"test\":\"concurrent-claim\"}",
                    OutboxEventStatus.PENDING,
                    LocalDateTime.now()
            );

            OutboxEvent savedEvent =
                    outboxRepository.save(event);

            eventIds.add(savedEvent.getEventId());
        }

        // 2. Synchronize two simulated relay workers
        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        // 3. Worker A attempts to claim the events
        Future<List<Long>> workerA =
                executor.submit(() -> {

                    startLatch.await();

                    return transactionTemplate.execute(status -> {

                        List<OutboxEvent> claimed =
                                outboxRepository.claimPendingEvents(
                                        LocalDateTime.now(),
                                        eventCount / 2
                                );

                        return claimed.stream()
                                .map(OutboxEvent::getEventId)
                                .toList();
                    });
                });

        // 4. Worker B attempts to claim the same pool of events
        Future<List<Long>> workerB =
                executor.submit(() -> {

                    startLatch.await();

                    return transactionTemplate.execute(status -> {

                        List<OutboxEvent> claimed =
                                outboxRepository.claimPendingEvents(
                                        LocalDateTime.now(),
                                        eventCount / 2
                                );

                        return claimed.stream()
                                .map(OutboxEvent::getEventId)
                                .toList();
                    });
                });

        // 5. Release both workers at approximately the same time
        startLatch.countDown();

        // 6. Wait for both workers to finish
        List<Long> workerAClaims = workerA.get();
        List<Long> workerBClaims = workerB.get();

        executor.shutdown();

        // 7. Combine all claims
        List<Long> allClaims =
                new ArrayList<>();

        allClaims.addAll(workerAClaims);
        allClaims.addAll(workerBClaims);

        // 8. Verify that both workers received events
        assertFalse(
                workerAClaims.isEmpty(),
                "Worker A should claim events"
        );

        assertFalse(
                workerBClaims.isEmpty(),
                "Worker B should claim events"
        );

        // 9. Verify that no event was claimed twice
        Set<Long> uniqueClaims =
                new HashSet<>(allClaims);

        assertEquals(
                allClaims.size(),
                uniqueClaims.size(),
                "The same outbox event must never be claimed by two workers"
        );

        // 10. Verify that all claimed events belong to this test
        assertTrue(
                eventIds.containsAll(allClaims),
                "Workers must only claim events created by this test"
        );

        // 11. Verify that the database state is PROCESSING
        for (Long eventId : allClaims) {

            OutboxEvent event =
                    outboxRepository
                            .findById(eventId)
                            .orElseThrow();

            assertEquals(
                    OutboxEventStatus.PROCESSING,
                    event.getStatus()
            );

            assertNotNull(
                    event.getProcessingStartedAt()
            );
        }

        // 12. Clean up test events
        outboxRepository.deleteAllById(eventIds);
    }
}