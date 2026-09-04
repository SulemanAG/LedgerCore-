package com.example.ledgercore.repository;

import com.example.ledgercore.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Finds all outbox events belonging to a financial transaction.
     *
     * @param aggregateId financial transaction ID
     * @return outbox events associated with the transaction
     */
    List<OutboxEvent> findByAggregateId(Long aggregateId);
}