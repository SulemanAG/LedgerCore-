package com.example.ledgercore.event;

import com.example.ledgercore.outbox.DepositEventPayload;
import com.example.ledgercore.outbox.TransferEventPayload;
import com.example.ledgercore.outbox.WithdrawalEventPayload;
import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Processes Kafka events and provides durable consumer-side idempotency.
 *
 * <p>
 * Duplicate events are detected using the processed-events table.
 * Concurrent duplicate deliveries are protected by a unique database
 * constraint on event_id.
 * </p>
 *
 * <p>
 * Successfully processed financial events also update the Redis
 * account-balance projection using the authoritative post-transaction
 * balances contained in the event payload.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class EventProcessingService {

    private final ProcessedEventRepository processedEventRepository;
    private final ProcessedEventService processedEventService;
    private final AccountBalanceRedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * Creates the event processing service.
     *
     * @param processedEventRepository repository used to check
     *                                  processed events
     * @param processedEventService service responsible for recording
     *                              processed events
     * @param redisService Redis account-balance projection service
     * @param objectMapper Jackson object mapper used to deserialize
     *                     event payloads
     */
    public EventProcessingService(
            ProcessedEventRepository processedEventRepository,
            ProcessedEventService processedEventService,
            AccountBalanceRedisService redisService,
            ObjectMapper objectMapper
    ) {
        this.processedEventRepository = processedEventRepository;
        this.processedEventService = processedEventService;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a Kafka event with durable idempotency protection
     * and updates the Redis balance projection.
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

            // 3. Update the Redis projection.
            updateRedisProjection(event);

            // 4. Record successful processing.
            processedEventService.save(event);

        } catch (DataIntegrityViolationException exception) {

            // 5. Another thread inserted the same event first.
            System.out.println(
                    "Concurrent duplicate Kafka event ignored: "
                            + event.eventId()
            );
        }
    }

    /**
     * Updates Redis using the balance values contained in the
     * completed transaction event.
     *
     * @param event Kafka event
     */
    private void updateRedisProjection(KafkaEvent event) {

        try {

            switch (event.eventType()) {

                case "DEPOSIT_COMPLETED" -> {

                    DepositEventPayload payload =
                            objectMapper.readValue(
                                    event.payload(),
                                    DepositEventPayload.class
                            );

                    redisService.setBalance(
                            payload.accountId(),
                            payload.balanceAfter()
                    );
                }

                case "WITHDRAWAL_COMPLETED" -> {

                    WithdrawalEventPayload payload =
                            objectMapper.readValue(
                                    event.payload(),
                                    WithdrawalEventPayload.class
                            );

                    redisService.setBalance(
                            payload.accountId(),
                            payload.balanceAfter()
                    );
                }

                case "TRANSFER_COMPLETED" -> {

                    TransferEventPayload payload =
                            objectMapper.readValue(
                                    event.payload(),
                                    TransferEventPayload.class
                            );

                    redisService.setBalance(
                            payload.sourceAccountId(),
                            payload.sourceBalanceAfter()
                    );

                    redisService.setBalance(
                            payload.destinationAccountId(),
                            payload.destinationBalanceAfter()
                    );
                }

                default -> {

                    System.out.println(
                            "No Redis projection handler for event type: "
                                    + event.eventType()
                    );
                }
            }

        } catch (Exception exception) {

            // 6. Convert the checked deserialization exception into
            // an unchecked exception so Kafka can retry the event.
            throw new IllegalStateException(
                    "Failed to process Kafka event "
                            + event.eventId(),
                    exception
            );
        }
    }
}