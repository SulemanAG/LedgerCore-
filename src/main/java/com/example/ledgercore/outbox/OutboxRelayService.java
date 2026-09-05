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
 * The relay periodically searches for PENDING outbox events whose
 * retry time has arrived. Each event is converted into a Kafka event
 * envelope and published asynchronously.
 * </p>
 *
 * <p>
 * Successful events are marked as PUBLISHED. Failed events remain
 * retryable until the maximum retry count is reached, after which
 * they are marked as FAILED.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class OutboxRelayService {

    /**
     * Maximum number of failed publication attempts allowed
     * before an event is permanently marked as FAILED.
     */
    private static final int MAX_RETRIES = 5;

    /**
     * Initial retry delay in seconds.
     */
    private static final long INITIAL_RETRY_DELAY_SECONDS = 2;

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the outbox relay service.
     *
     * @param outboxRepository repository used to access outbox events
     * @param kafkaProducerService service used to publish events to Kafka
     * @param objectMapper Jackson object mapper used to create Kafka envelopes
     */
    public OutboxRelayService(
            OutboxRepository outboxRepository,
            KafkaProducerService kafkaProducerService,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls PostgreSQL for pending outbox events whose retry time
     * has arrived.
     *
     * <p>
     * The relay runs every five seconds during development.
     * </p>
     */
    @Scheduled(fixedDelay = 5000)
    public void relayPendingEvents() {

        LocalDateTime now = LocalDateTime.now();

        List<OutboxEvent> events =
                outboxRepository
                        .findByStatusAndNextAttemptLessThanEqualOrderByCreatedAtAsc(
                                OutboxEventStatus.PENDING,
                                now
                        );

        for (OutboxEvent event : events) {

            // 1. Mark the event as PROCESSING before publishing.
            event.setStatus(OutboxEventStatus.PROCESSING);
            outboxRepository.save(event);

            // 2. Publish the event asynchronously.
            publishEvent(event);
        }
    }

    /**
     * Publishes a single outbox event to Kafka.
     *
     * @param event outbox event to publish
     */
    private void publishEvent(OutboxEvent event) {

        try {

            // 1. Create the Kafka event envelope.
            KafkaEvent kafkaEvent = new KafkaEvent(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getCreatedAt(),
                    event.getPayload()
            );

            // 2. Serialize the envelope into JSON.
            String message =
                    objectMapper.writeValueAsString(kafkaEvent);

            // 3. Publish the envelope to Kafka.
            kafkaProducerService
                    .publish(
                            event.getEventId().toString(),
                            message
                    )
                    .whenComplete((result, exception) -> {

                        if (exception == null) {

                            // 4. Kafka acknowledged the event.
                            markPublished(event.getEventId());

                        } else {

                            // 5. Kafka publication failed.
                            markFailed(
                                    event.getEventId(),
                                    exception
                            );
                        }
                    });

        } catch (Exception exception) {

            // 6. Serialization or another synchronous failure.
            markFailed(
                    event.getEventId(),
                    exception
            );
        }
    }

    /**
     * Marks an outbox event as successfully published.
     *
     * @param eventId outbox event ID
     */
    public void markPublished(Long eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        // 1. Mark the event as successfully published.
        event.setStatus(OutboxEventStatus.PUBLISHED);

        // 2. Record the publication timestamp.
        event.setPublishedAt(LocalDateTime.now());

        // 3. Clear any previous failure information.
        event.setLastError(null);

        outboxRepository.save(event);
    }

    /**
     * Records a failed Kafka publication attempt.
     *
     * <p>
     * The event is returned to PENDING when retry attempts remain.
     * Exponential backoff determines when the next attempt should occur.
     * Once the maximum retry count is reached, the event is permanently
     * marked as FAILED.
     * </p>
     *
     * @param eventId outbox event ID
     * @param exception exception produced by the failed publication
     */
    public void markFailed(
            Long eventId,
            Throwable exception
    ) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        // 1. Increase the retry count.
        int retryCount = event.getRetryCount() + 1;

        event.setRetryCount(retryCount);

        // 2. Store the failure reason for debugging.
        event.setLastError(
                exception.getMessage()
        );

        // 3. Check whether the maximum retry count was reached.
        if (retryCount >= MAX_RETRIES) {

            event.setStatus(
                    OutboxEventStatus.FAILED
            );

            event.setNextAttempt(null);

        } else {

            // 4. Return the event to PENDING for another attempt.
            event.setStatus(
                    OutboxEventStatus.PENDING
            );

            // 5. Calculate exponential backoff.
            long delaySeconds =
                    INITIAL_RETRY_DELAY_SECONDS
                            * (1L << (retryCount - 1));

            // 6. Schedule the next publication attempt.
            event.setNextAttempt(
                    LocalDateTime.now()
                            .plusSeconds(delaySeconds)
            );
        }

        // 7. Persist the new state.
        outboxRepository.save(event);
    }
}