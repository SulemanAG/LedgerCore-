package com.example.ledgercore.outbox;

import com.example.ledgercore.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Manages transactional state transitions for outbox events.
 *
 * <p>
 * Each state transition uses an independent database transaction.
 * This ensures that asynchronous Kafka callbacks can safely update
 * outbox state after the original relay operation has completed.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class OutboxStateService {

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

    /**
     * Creates the outbox state service.
     *
     * @param outboxRepository repository used to update outbox events
     */
    public OutboxStateService(
            OutboxRepository outboxRepository
    ) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Marks an outbox event as PROCESSING.
     *
     * @param eventId outbox event ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        // 1. Mark the event as PROCESSING.
        event.setStatus(
                OutboxEventStatus.PROCESSING
        );

        // 2. Record when processing started.
        event.setProcessingStartedAt(
                LocalDateTime.now()
        );

        // 3. Persist the state transition.
        outboxRepository.save(event);
    }

    /**
     * Marks an outbox event as successfully published.
     *
     * @param eventId outbox event ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        // 1. Mark the event as successfully published.
        event.setStatus(
                OutboxEventStatus.PUBLISHED
        );

        // 2. Record the publication timestamp.
        event.setPublishedAt(
                LocalDateTime.now()
        );

        // 3. Clear the processing timestamp.
        event.setProcessingStartedAt(null);

        // 4. Clear previous failure information.
        event.setLastError(null);

        // 5. Persist the successful state.
        outboxRepository.save(event);
    }

    /**
     * Records a failed Kafka publication attempt.
     *
     * <p>
     * The event is returned to PENDING while retry attempts remain.
     * Exponential backoff determines when the next attempt occurs.
     * Once the maximum retry count is reached, the event is marked
     * as FAILED.
     * </p>
     *
     * @param eventId outbox event ID
     * @param exception exception produced by the failed publication
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        int retryCount =
                event.getRetryCount() + 1;

        event.setRetryCount(retryCount);

        // 2. Store the failure reason.
        event.setLastError(
                exception.getMessage()
        );

        // 3. Clear the processing timestamp.
        event.setProcessingStartedAt(null);

        // 4. Check whether the maximum retry count was reached.
        if (retryCount >= MAX_RETRIES) {

            event.setStatus(
                    OutboxEventStatus.FAILED
            );

            event.setNextAttempt(null);

        } else {

            // 5. Return the event to PENDING.
            event.setStatus(
                    OutboxEventStatus.PENDING
            );

            // 6. Calculate exponential backoff.
            long delaySeconds =
                    INITIAL_RETRY_DELAY_SECONDS
                            * (1L << (retryCount - 1));

            // 7. Schedule the next retry.
            event.setNextAttempt(
                    LocalDateTime.now()
                            .plusSeconds(delaySeconds)
            );
        }

        // 8. Persist the new state.
        outboxRepository.save(event);
    }

    /**
     * Recovers an outbox event that has remained in PROCESSING
     * longer than the allowed processing timeout.
     *
     * @param eventId outbox event ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverStaleProcessingEvent(Long eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElse(null);

        if (event == null) {
            return;
        }

        // 1. Return the event to PENDING.
        event.setStatus(
                OutboxEventStatus.PENDING
        );

        // 2. Make the event immediately eligible for retry.
        event.setNextAttempt(
                LocalDateTime.now()
        );

        // 3. Clear the processing timestamp.
        event.setProcessingStartedAt(null);

        // 4. Store recovery information.
        event.setLastError(
                "Recovered stale PROCESSING event"
        );

        // 5. Persist the recovered state.
        outboxRepository.save(event);
    }
}