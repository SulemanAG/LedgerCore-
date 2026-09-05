package com.example.ledgercore.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration for LedgerCore.
 *
 * <p>
 * Defines the application topics used for financial transaction
 * events and failed-event handling.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Configuration
public class KafkaConfig {

    /**
     * Main LedgerCore transaction topic.
     *
     * <p>
     * Three partitions are used for the current development setup.
     * </p>
     *
     * @return Kafka transaction topic
     */
    @Bean
    public NewTopic transactionTopic() {

        return TopicBuilder
                .name("ledgercore-transactions")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic for events that cannot be processed
     * after the configured retry attempts.
     *
     * @return Kafka dead letter topic
     */
    @Bean
    public NewTopic transactionDeadLetterTopic() {

        return TopicBuilder
                .name("ledgercore-transactions.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }
}