package com.example.ledgercore.repository;

import com.example.ledgercore.outbox.OutboxEvent;
import com.example.ledgercore.outbox.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Finds all outbox events associated with a financial transaction.
     *
     * @param aggregateId financial transaction ID
     * @return matching outbox events
     */
    List<OutboxEvent> findByAggregateId(Long aggregateId);

    /**
     * Finds outbox events with the specified status
     * in ascending creation-time order.
     *
     * @param status outbox event status
     * @return matching outbox events ordered from oldest to newest
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );
}