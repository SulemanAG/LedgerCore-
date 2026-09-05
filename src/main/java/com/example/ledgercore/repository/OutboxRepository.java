package com.example.ledgercore.repository;

import com.example.ledgercore.outbox.OutboxEvent;
import com.example.ledgercore.outbox.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for transactional outbox events.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Finds all events associated with a financial transaction.
     *
     * @param aggregateId financial transaction ID
     * @return matching events
     */
    List<OutboxEvent> findByAggregateId(Long aggregateId);

    /**
     * Finds outbox events with the specified status
     * in ascending creation-time order.
     *
     * @param status outbox event status
     * @return matching outbox events
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );

    /**
     * Finds pending events whose retry time has arrived.
     *
     * @param status expected event status
     * @param currentTime current time
     * @return events ready for another attempt
     */
    List<OutboxEvent> findByStatusAndNextAttemptLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus status,
            LocalDateTime currentTime
    );
}