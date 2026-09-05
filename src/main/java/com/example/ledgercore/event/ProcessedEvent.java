package com.example.ledgercore.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents an event that has already been processed by a Kafka consumer.
 *
 * <p>This table provides durable consumer-side idempotency. If Kafka
 * delivers the same event more than once, the consumer can determine
 * whether the event has already been processed.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Setter
@Getter
@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_processed_event_id",
                        columnNames = "event_id"
                )
        }
)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier of the Kafka event.
     */
    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    /**
     * Type of event that was processed.
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * Time at which the event was successfully processed.
     */
    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
    }

    /**
     * Creates a processed-event record.
     *
     * @param eventId event identifier
     * @param eventType event type
     * @param processedAt processing timestamp
     */
    public ProcessedEvent(
            Long eventId,
            String eventType,
            LocalDateTime processedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

}