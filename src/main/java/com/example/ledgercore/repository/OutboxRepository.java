package com.example.ledgercore.repository;

import com.example.ledgercore.outbox.OutboxEvent;
import com.example.ledgercore.outbox.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Finds stale PROCESSING events.
     *
     * @param status expected event status
     * @param cutoff processing start cutoff
     * @return stale processing events
     */
    List<OutboxEvent> findByStatusAndProcessingStartedAtLessThanEqualOrderByProcessingStartedAtAsc(
            OutboxEventStatus status,
            LocalDateTime cutoff
    );

    /**
     * Atomically claims pending outbox events for a relay worker.
     *
     * <p>
     * PostgreSQL row-level locks are acquired using FOR UPDATE.
     * SKIP LOCKED prevents one relay worker from waiting for rows
     * already claimed by another worker.
     * </p>
     *
     * <p>
     * The selected events are changed from PENDING to PROCESSING
     * inside the same database transaction.
     * </p>
     *
     * @param currentTime current time used for retry eligibility
     * @param limit maximum number of events to claim
     * @return events successfully claimed by this worker
     */
    @Query(
            value = """
                    UPDATE outbox_events
                    SET status = 'PROCESSING',
                        processing_started_at = CURRENT_TIMESTAMP
                    WHERE event_id IN (
                        SELECT event_id
                        FROM outbox_events
                        WHERE status = 'PENDING'
                          AND next_attempt <= :currentTime
                        ORDER BY created_at ASC
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                    )
                    RETURNING *
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> claimPendingEvents(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("limit") int limit
    );
}