package com.example.ledgercore.repository;

import com.example.ledgercore.event.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for tracking Kafka events that have already been processed.
 * @author Suleman Agasimani
 */
public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, Long> {

    /**
     * Finds a processed event using its Kafka event ID.
     *
     * @param eventId event identifier
     * @return processed event if it exists
     */
    Optional<ProcessedEvent> findByEventId(Long eventId);
}