package com.example.ledgercore.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Configuration for LedgerCore.
 *
 * <p>
 *     Defines the kafka topics used by the applications
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Configuration
public class KafkaConfig {

    /**
     * Creates the LedgerCore transaction topic
     *
     * <p>
     *     The topic is divided into three partitions so that
     *     consumers can process events concurrently in the future.
     * </p>
     *
     * @return Kakfa topic definition
     */

    @Bean
    public NewTopic transactionTopic(){
        return TopicBuilder
                .name("ledgercore-transactions")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
