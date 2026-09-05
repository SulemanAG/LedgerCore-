package com.example.ledgercore.event;

import java.time.LocalDateTime;

/**
 * Generic envelope used for events published to Kafka.
 *
 * @param eventId unique outbox event identifier
 * @param eventType type of financial event
 * @param aggregateId associated transaction ID
 * @param occurredAt time at which the event was created
 * @param payload serialized financial event payload
 */
public record KafkaEvent(
        Long eventId,
        String eventType,
        Long aggregateId,
        LocalDateTime occurredAt,
        String payload
) {
}