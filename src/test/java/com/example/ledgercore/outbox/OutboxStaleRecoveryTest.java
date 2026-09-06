package com.example.ledgercore.outbox;

import com.example.ledgercore.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for stale transactional outbox recovery.
 *
 * <p>
 * These tests verify that an outbox event which remains in
 * PROCESSING state beyond the allowed timeout can be recovered
 * and returned to PENDING state.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class OutboxStaleRecoveryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Test
    void shouldRecoverStaleProcessingEvent() {

        // 1. Create an event that represents a crashed relay attempt
        OutboxEvent event = new OutboxEvent(
                "STALE_RECOVERY_TEST",
                999999L,
                "{\"test\":\"stale-recovery\"}",
                OutboxEventStatus.PROCESSING,
                LocalDateTime.now().minusMinutes(2)
        );

        event.setProcessingStartedAt(
                LocalDateTime.now().minusMinutes(2)
        );

        OutboxEvent savedEvent = outboxRepository.save(event);

        Long eventId = savedEvent.getEventId();

        // 2. Verify that the event was initially stored as PROCESSING
        OutboxEvent processingEvent =
                outboxRepository.findById(eventId).orElseThrow();

        assertEquals(
                OutboxEventStatus.PROCESSING,
                processingEvent.getStatus()
        );

        assertNotNull(
                processingEvent.getProcessingStartedAt()
        );

        // 3. Run stale-event recovery
        outboxRelayService.recoverStaleProcessingEvents();

        // 4. Reload the event from PostgreSQL
        OutboxEvent recoveredEvent =
                outboxRepository.findById(eventId).orElseThrow();

        // 5. Verify that the event returned to PENDING
        assertEquals(
                OutboxEventStatus.PENDING,
                recoveredEvent.getStatus()
        );

        // 6. Verify that the event is immediately eligible for retry
        assertNotNull(
                recoveredEvent.getNextAttempt()
        );

        assertTrue(
                !recoveredEvent.getNextAttempt()
                        .isAfter(LocalDateTime.now())
        );

        // 7. Verify that processing state was cleared
        assertNull(
                recoveredEvent.getProcessingStartedAt()
        );

        // 8. Verify that recovery information was recorded
        assertEquals(
                "Recovered stale PROCESSING event",
                recoveredEvent.getLastError()
        );

        // 9. Clean up the test event
        outboxRepository.deleteById(eventId);
    }
}