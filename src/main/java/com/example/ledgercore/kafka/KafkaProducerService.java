package com.example.ledgercore.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing LedgerCore events to Kafka.
 *
 * <p>
 * This service provides a small abstraction over Spring Kafka's
 * {@link KafkaTemplate}. The outbox relay uses this service to
 * publish durable PostgreSQL outbox events to Kafka.
 * </p>
 */
@Service
public class KafkaProducerService {

    private static final String TRANSACTION_TOPIC =
            "ledgercore-transactions";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Creates the Kafka producer service.
     *
     * @param kafkaTemplate Spring Kafka template used to publish messages
     */
    public KafkaProducerService(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an event to the LedgerCore transaction topic.
     *
     * <p>
     * The returned future completes when Kafka acknowledges the send
     * or completes exceptionally when the send fails.
     * </p>
     *
     * @param key Kafka message key
     * @param message serialized event payload
     * @return future representing the Kafka send operation
     */
    public CompletableFuture<SendResult<String, String>> publish(
            String key,
            String message
    ) {

        return kafkaTemplate.send(
                TRANSACTION_TOPIC,
                key,
                message
        );
    }
}