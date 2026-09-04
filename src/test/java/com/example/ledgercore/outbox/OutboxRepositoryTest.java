package com.example.ledgercore.outbox;

import com.example.ledgercore.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    /**
     * Verifies that pending outbox events are returned
     * in ascending creation-time order.
     */
    @Test
    void shouldFindPendingEventsInCreationOrder() {

        // 1. Create a unique event type for this test.
        String testEventType = "OUTBOX_REPOSITORY_TEST";

        // 2. Create an older pending event.
        OutboxEvent olderEvent =
                new OutboxEvent(
                        testEventType,
                        999001L,
                        "{\"test\":\"older\"}",
                        OutboxEventStatus.PENDING,
                        LocalDateTime.now().minusMinutes(2)
                );

        // 3. Create a newer pending event.
        OutboxEvent newerEvent =
                new OutboxEvent(
                        testEventType,
                        999002L,
                        "{\"test\":\"newer\"}",
                        OutboxEventStatus.PENDING,
                        LocalDateTime.now().minusMinutes(1)
                );

        // 4. Save both test events.
        outboxRepository.save(olderEvent);
        outboxRepository.save(newerEvent);

        // 5. Flush the events so they are definitely persisted.
        outboxRepository.flush();

        // 6. Query all pending events.
        List<OutboxEvent> pendingEvents =
                outboxRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING
                );

        // 7. Find the position of our older test event.
        int olderEventIndex = -1;

        for (int i = 0; i < pendingEvents.size(); i++) {
            if (pendingEvents.get(i).getAggregateId().equals(999001L)) {
                olderEventIndex = i;
                break;
            }
        }

        // 8. Find the position of our newer test event.
        int newerEventIndex = -1;

        for (int i = 0; i < pendingEvents.size(); i++) {
            if (pendingEvents.get(i).getAggregateId().equals(999002L)) {
                newerEventIndex = i;
                break;
            }
        }

        // 9. Verify that the older test event was found.
        assertEquals(false, olderEventIndex == -1);

        // 10. Verify that the newer test event was found.
        assertEquals(false, newerEventIndex == -1);

        // 11. Verify that the older event appears before the newer event.
        assertEquals(true, olderEventIndex < newerEventIndex);

        // 12. Cleanup only the events created by this test.
        outboxRepository.delete(olderEvent);
        outboxRepository.delete(newerEvent);

        // 13. Flush the cleanup.
        outboxRepository.flush();
    }
}