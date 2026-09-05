package com.example.ledgercore.outbox;

/**
 * Represents the lifecycle state of an outbox event.
 *
 * <p>An event starts as PENDING after the financial
 * transaction commits. The relay claims it for processing,
 * publishes it to Kafka, and finally marks it as PUBLISHED.</p>
 */
public enum OutboxEventStatus {

    /**
     * Event is waiting to be published.
     */
    PENDING,

    /**
     * Event has been claimed by the relay and is currently
     * being published.
     */
    PROCESSING,

    /**
     * Event was successfully published to Kafka.
     */
    PUBLISHED,

    /**
     * Event exceeded the allowed retry limit.
     */
    FAILED
}