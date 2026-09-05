package com.example.ledgercore.event;

import com.example.ledgercore.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Processes Kafka events and provides durable consumer-side idempotency.
 *
 * <p>
 * Duplicate events are detected using the processed-events table.
 * Concurrent duplicate deliveries are protected by a unique database
 * constraint on event_id.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class EventProcessingService {

    private final ProcessedEventRepository processedEventRepository;
    private final ProcessedEventService processedEventService;

    /**
     * Creates the event processing service.
     *
     * @param processedEventRepository repository used to check
     *                                  processed events
     * @param processedEventService service responsible for recording
     *                              processed events
     */
    public EventProcessingService(
            ProcessedEventRepository processedEventRepository,
            ProcessedEventService processedEventService
    ) {
        this.processedEventRepository = processedEventRepository;
        this.processedEventService = processedEventService;
    }

    /**
     * Processes a Kafka event with durable idempotency protection.
     *
     * @param event Kafka event
     */
    public void process(KafkaEvent event) {

        // 1. Check whether the event already exists.
        if (processedEventRepository
                .findByEventId(event.eventId())
                .isPresent()) {

            System.out.println(
                    "Duplicate Kafka event ignored: "
                            + event.eventId()
            );

            return;
        }

        // 2. Process the event.
        System.out.println("Processing Kafka event:");
        System.out.println("Event ID: " + event.eventId());
        System.out.println("Event Type: " + event.eventType());
        System.out.println("Aggregate ID: " + event.aggregateId());
        System.out.println("Payload: " + event.payload());

        try {

            // 3. Record successful processing.
            processedEventService.save(event);

        } catch (DataIntegrityViolationException exception) {

            // 4. Another thread inserted the same event first.
            System.out.println(
                    "Concurrent duplicate Kafka event ignored: "
                            + event.eventId()
            );
        }
    }
}