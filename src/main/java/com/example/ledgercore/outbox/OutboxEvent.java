package com.example.ledgercore.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents a transactional outbox event.
 *
 * <p>
 * Outbox events are stored in PostgreSQL together with the financial
 * transaction that produced them. A separate relay later publishes
 * these events to Kafka.
 * </p>
 *
 * <p>
 * PostgreSQL remains the durable source of truth for the outbox.
 * Kafka publication is an asynchronous downstream operation.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Setter
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    /**
     * Unique database identifier for the outbox event.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    /**
     * Type of event being published.
     */
    @Column(
            nullable = false,
            length = 50
    )
    private String eventType;

    /**
     * Identifier of the aggregate associated with the event.
     */
    @Column(nullable = false)
    private Long aggregateId;

    /**
     * Serialized event payload.
     */
    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String payload;

    /**
     * Current lifecycle state of the outbox event.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OutboxEventStatus status;

    /**
     * Time at which the outbox event was created.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Time at which the event was successfully published to Kafka.
     */
    private LocalDateTime publishedAt;

    /**
     * Number of failed publication attempts.
     */
    @Column(nullable = false)
    private int retryCount;

    /**
     * Time after which another publication attempt may occur.
     */
    @Column(name = "next_attempt")
    private LocalDateTime nextAttempt;

    /**
     * Time at which the event entered PROCESSING state.
     *
     * <p>
     * This timestamp allows the relay to identify events that became
     * stuck while being processed.
     * </p>
     */
    private LocalDateTime processingStartedAt;

    /**
     * Most recent publication failure reason.
     */
    @Column(columnDefinition = "TEXT")
    private String lastError;

    /**
     * Default constructor required by JPA.
     */
    public OutboxEvent() {
    }

    /**
     * Creates a new outbox event.
     *
     * <p>
     * This constructor is kept compatible with the existing
     * transaction, deposit, withdrawal, and outbox test code.
     * </p>
     *
     * @param eventType event type
     * @param aggregateId aggregate identifier
     * @param payload serialized event payload
     * @param status initial outbox status
     * @param createdAt creation timestamp
     */
    public OutboxEvent(
            String eventType,
            Long aggregateId,
            String payload,
            OutboxEventStatus status,
            LocalDateTime createdAt
    ) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.retryCount = 0;
        this.nextAttempt = createdAt;
        this.processingStartedAt = null;
    }

}