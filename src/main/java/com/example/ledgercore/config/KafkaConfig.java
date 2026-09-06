package com.example.ledgercore.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration for LedgerCore.
 *
 * <p>
 * Defines application topics and consumer error handling / retry / DLT recovery.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Configuration
@EnableKafka
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

    /**
     * Configures the Kafka consumer error handler with a bounded retry policy
     * and dead-letter publishing recoverer.
     *
     * <p>
     * Bounded retry policy: 3 total attempts (1 initial try + 2 retries) with a 1,000ms fixed backoff.
     * Permanently failing events are forwarded to "ledgercore-transactions.DLT" preserving
     * original key, payload, and Spring Kafka DLT exception headers.
     * </p>
     *
     * @param kafkaTemplate template used by DLT recoverer to publish failed events
     * @return consumer error handler
     */
    @Bean
    public CommonErrorHandler commonErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition("ledgercore-transactions.DLT", record.partition())
        );

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 2L)
        );
    }

    /**
     * Explicitly sets up ConcurrentKafkaListenerContainerFactory with the configured
     * CommonErrorHandler (retries + DLT recoverer).
     *
     * @param consumerFactory Spring Kafka consumer factory
     * @param commonErrorHandler configured error handler
     * @return listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler commonErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(commonErrorHandler);
        return factory;
    }
}