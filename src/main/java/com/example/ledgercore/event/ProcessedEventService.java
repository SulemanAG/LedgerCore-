package com.example.ledgercore.event;

import com.example.ledgercore.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles persistence of successfully processed Kafka events.
 *
 * <p>
 * The persistence operation uses an independent transaction so that
 * a concurrent duplicate can be safely detected using the database
 * unique constraint.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Creates the processed-event service.
     *
     * @param processedEventRepository repository used to persist
     *                                  processed event records
     */
    public ProcessedEventService(
            ProcessedEventRepository processedEventRepository
    ) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Records a successfully processed Kafka event.
     *
     * <p>
     * A new transaction is created for this operation. If another
     * consumer concurrently inserts the same event ID, PostgreSQL's
     * unique constraint prevents a duplicate record.
     *
     * @param event Kafka event
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(KafkaEvent event) {

        // 1. Create a processed-event record.
        ProcessedEvent processedEvent = new ProcessedEvent(
                event.eventId(),
                event.eventType(),
                LocalDateTime.now()
        );

        // 2. Persist immediately so constraint violations are raised
        //    inside this transaction.
        processedEventRepository.saveAndFlush(processedEvent);
    }
}