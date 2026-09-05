package com.example.ledgercore.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Configuration for Kafka consumer error handling.
 *
 * <p>
 * Failed events are retried using exponential backoff. Events that
 * continue to fail after the configured retry attempts are published
 * to the Dead Letter Topic.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Creates the Kafka error handler.
     *
     * @param kafkaTemplate Kafka operations used to publish failed
     *                      events to the DLT
     * @return configured Kafka error handler
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaOperations<Object, Object> kafkaTemplate
    ) {

        // 1. Configure the Dead Letter Topic publisher.
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + ".DLT",
                                        record.partition()
                                )
                );

        // 2. Configure exponential retry backoff.
        ExponentialBackOff backOff =
                new ExponentialBackOff(
                        1000L,
                        2.0
                );

        // 3. Allow three retry attempts.
        backOff.setMaxElapsedTime(7000L);

        // 4. Create the error handler.
        return new DefaultErrorHandler(
                recoverer,
                backOff
        );
    }
}