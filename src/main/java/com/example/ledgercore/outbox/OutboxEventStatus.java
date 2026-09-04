package com.example.ledgercore.outbox;

/**
 * Represents the processing state of an outbox event.
 *
 * <p>
 * An event starts in the PENDING state. The future outbox relay will
 * publish the event to Kafka and then change its state to PUBLISHED.
 * </p>
 */
public enum OutboxEventStatus {

    /**
     * Event has been created but has not yet been published.
     */
    PENDING,

    /**
     * Event has been successfully published.
     */
    PUBLISHED,

    /**
     * Event publication has failed permanently.
     */
    FAILED
}