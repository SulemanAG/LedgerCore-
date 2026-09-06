package com.example.ledgercore.outbox;

import com.example.ledgercore.event.KafkaEvent;
import com.example.ledgercore.kafka.KafkaProducerService;
import com.example.ledgercore.repository.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Relays transactional outbox events from PostgreSQL to Kafka.
 *
 * <p>
 * The relay atomically claims PENDING events from the outbox table
 * and marks them as PROCESSING before publishing them to Kafka.
 * </p>
 *
 * <p>
 * Kafka message keys are based on the aggregate account ID so that
 * events belonging to the same account are routed to the same Kafka
 * partition, allowing Kafka to preserve their ordering.
 * </p>
 *
 * <p>
 * Successfully published events are marked as PUBLISHED.
 * Failed events are returned to PENDING with exponential backoff,
 * or marked as FAILED after the maximum retry count is reached.
 * </p>
 *
 * <p>
 * The relay also recovers stale PROCESSING events in case the
 * application crashes after claiming an event but before Kafka
 * publication completes.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class OutboxRelayService {

    private static final int BATCH_SIZE = 100;

    private static final long PROCESSING_TIMEOUT_SECONDS = 30;

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final OutboxStateService outboxStateService;

    /**
     * Creates the outbox relay service.
     *
     * @param outboxRepository repository used to claim and recover events
     * @param kafkaProducerService service used to publish events to Kafka
     * @param objectMapper mapper used to serialize Kafka events
     * @param outboxStateService service used to manage outbox event states
     */
    public OutboxRelayService(
            OutboxRepository outboxRepository,
            KafkaProducerService kafkaProducerService,
            ObjectMapper objectMapper,
            OutboxStateService outboxStateService
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
        this.outboxStateService = outboxStateService;
    }

    /**
     * Claims a batch of pending outbox events and publishes them to Kafka.
     *
     * <p>
     * Events are atomically claimed using PostgreSQL row locking and
     * {@code SKIP LOCKED}. This allows multiple application instances
     * to process different events concurrently without claiming the
     * same event.
     * </p>
     */
    @Scheduled(fixedDelay = 5000)
    public void relayPendingEvents() {

        LocalDateTime now = LocalDateTime.now();

        System.out.println(
                "OUTBOX RELAY: polling at " + now
        );

        List<OutboxEvent> events =
                outboxRepository.claimPendingEvents(
                        now,
                        BATCH_SIZE
                );

        System.out.println(
                "OUTBOX RELAY: claimed "
                        + events.size()
                        + " pending events"
        );

        for (OutboxEvent event : events) {

            System.out.println(
                    "OUTBOX RELAY: processing event "
                            + event.getEventId()
                            + " ["
                            + event.getEventType()
                            + "]"
            );

            /*
             * The event has already been marked PROCESSING
             * by claimPendingEvents().
             *
             * Therefore, we do NOT call markProcessing() here.
             */

            publishEvent(event);
        }
    }

    /**
     * Recovers outbox events that have remained in PROCESSING state
     * beyond the configured timeout.
     *
     * <p>
     * This protects against a process crash occurring after an event
     * was claimed but before Kafka acknowledged the message.
     * </p>
     */
    @Scheduled(fixedDelay = 5000)
    public void recoverStaleProcessingEvents() {

        LocalDateTime cutoff =
                LocalDateTime.now()
                        .minusSeconds(PROCESSING_TIMEOUT_SECONDS);

        List<OutboxEvent> staleEvents =
                outboxRepository
                        .findByStatusAndProcessingStartedAtLessThanEqualOrderByProcessingStartedAtAsc(
                                OutboxEventStatus.PROCESSING,
                                cutoff
                        );

        if (staleEvents.isEmpty()) {
            return;
        }

        System.out.println(
                "OUTBOX RECOVERY: found "
                        + staleEvents.size()
                        + " stale PROCESSING events"
        );

        for (OutboxEvent event : staleEvents) {

            System.out.println(
                    "OUTBOX RECOVERY: recovering event "
                            + event.getEventId()
            );

            outboxStateService.recoverStaleProcessingEvent(
                    event.getEventId()
            );
        }
    }

    /**
     * Serializes and publishes a single outbox event to Kafka.
     *
     * <p>
     * The Kafka key is the aggregate ID rather than the outbox event ID.
     * For account-based events, the aggregate ID represents the account
     * affected by the event.
     * </p>
     *
     * <p>
     * Using the aggregate ID as the Kafka key causes events for the same
     * aggregate to be routed to the same Kafka partition. Kafka preserves
     * message ordering within that partition.
     * </p>
     *
     * @param event outbox event to publish
     */
    private void publishEvent(OutboxEvent event) {

        try {

            KafkaEvent kafkaEvent =
                    new KafkaEvent(
                            event.getEventId(),
                            event.getEventType(),
                            event.getAggregateId(),
                            event.getCreatedAt(),
                            event.getPayload()
                    );

            String message =
                    objectMapper.writeValueAsString(kafkaEvent);

            /*
             * Use aggregateId as the Kafka key.
             *
             * Previously:
             *
             *     event.getEventId().toString()
             *
             * This could distribute events belonging to the same
             * account across different Kafka partitions.
             *
             * Now:
             *
             *     event.getAggregateId().toString()
             *
             * Events for the same aggregate use the same Kafka key
             * and therefore the same Kafka partition.
             */
            String kafkaKey =
                    event.getAggregateId().toString();

            kafkaProducerService
                    .publish(
                            kafkaKey,
                            message
                    )
                    .whenComplete((result, exception) -> {

                        if (exception == null) {

                            System.out.println(
                                    "OUTBOX RELAY: Kafka acknowledged event "
                                            + event.getEventId()
                            );

                            outboxStateService.markPublished(
                                    event.getEventId()
                            );

                        } else {

                            System.out.println(
                                    "OUTBOX RELAY: Kafka failed for event "
                                            + event.getEventId()
                                            + ": "
                                            + exception.getMessage()
                            );

                            outboxStateService.markFailed(
                                    event.getEventId(),
                                    exception
                            );
                        }
                    });

        } catch (Exception exception) {

            System.out.println(
                    "OUTBOX RELAY: synchronous failure for event "
                            + event.getEventId()
                            + ": "
                            + exception.getMessage()
            );

            outboxStateService.markFailed(
                    event.getEventId(),
                    exception
            );
        }
    }
}