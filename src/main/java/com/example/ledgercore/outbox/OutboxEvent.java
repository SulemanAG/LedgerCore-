package com.example.ledgercore.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents an event stored in the transactional outbox.
 *
 * <p>
 * The outbox event is persisted in the same PostgreSQL transaction as
 * the financial operation that generated it. This ensures that a
 * successfully committed financial operation always has a durable
 * event record that can later be published to an external system.
 * </p>
 * @author Suleman Agasimani
 * @since 1.0
 */
@Setter
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    /**
     * Unique identifier of the outbox event.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    /**
     * Type of event that occurred.
     *
     * <p>
     * Examples:
     * TRANSFER_COMPLETED,
     * DEPOSIT_COMPLETED,
     * WITHDRAWAL_COMPLETED
     * </p>
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * Identifier of the financial transaction associated with this event.
     */
    @Column(nullable = false)
    private Long aggregateId;

    /**
     * Serialized JSON representation of the event.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current processing status of the event.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    /**
     * Time at which the event was created.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Time at which the event was successfully published.
     *
     * <p>
     * This remains null while the event is pending.
     * </p>
     */
    private LocalDateTime publishedAt;

    /**
     * Number of publication attempts.
     */
    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextAttempt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    /**
     * Required by JPA.
     */
    public OutboxEvent() {
    }

    /**
     * Creates a new pending outbox event.
     *
     * @param eventType event type
     * @param aggregateId associated financial transaction ID
     * @param payload serialized event payload
     * @param status initial event status
     * @param createdAt event creation time
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
        this.nextAttempt=createdAt;
    }

}