package com.example.ledgercore.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying Kafka partitioning and ordering.
 *
 * <p>
 * Events using the same Kafka key must be routed to the same
 * Kafka partition. Kafka must then preserve their order within
 * that partition.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest
class KafkaEventOrderingTest {

    private static final String TOPIC =
            "ledgercore-transactions";

    private static final String TEST_KEY =
            "ORDERING_TEST_ACCOUNT_101";

    private static final String TEST_PREFIX =
            "ORDERING_TEST_EVENT_";

    private static final int EVENT_COUNT =
            5;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaConsumer<String, String> consumer;

    /**
     * Creates an isolated Kafka consumer for this test.
     */
    @BeforeEach
    void setUp() {

        Properties properties =
                new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "ledgercore-ordering-test-"
                        + UUID.randomUUID()
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "latest"
        );

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                "false"
        );

        consumer =
                new KafkaConsumer<>(
                        properties
                );
    }

    /**
     * Closes the Kafka consumer after every test.
     */
    @AfterEach
    void tearDown() {

        if (consumer != null) {
            consumer.close();
        }
    }

    /**
     * Verifies that events using the same Kafka key:
     *
     * <ul>
     *     <li>Use the same key.</li>
     *     <li>Are routed to the same partition.</li>
     *     <li>Maintain publishing order.</li>
     *     <li>Have increasing Kafka offsets.</li>
     * </ul>
     */
    @Test
    void shouldPreserveOrderingForSameAccount()
            throws Exception {

        // 1. Publish the first event and capture the actual Kafka partition.
        ProducerRecord<String, String> firstRecord =
                new ProducerRecord<>(
                        TOPIC,
                        TEST_KEY,
                        TEST_PREFIX + "1"
                );

        RecordMetadata firstMetadata =
                kafkaTemplate
                        .send(firstRecord)
                        .get(
                                10,
                                TimeUnit.SECONDS
                        )
                        .getRecordMetadata();

        int expectedPartition =
                firstMetadata.partition();

        long firstOffset =
                firstMetadata.offset();

        // 2. Publish the remaining events using the SAME Kafka key.
        List<String> expectedEvents =
                new ArrayList<>();

        expectedEvents.add(
                TEST_PREFIX + "1"
        );

        for (int i = 2; i <= EVENT_COUNT; i++) {

            String message =
                    TEST_PREFIX + i;

            expectedEvents.add(message);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                            TOPIC,
                            TEST_KEY,
                            message
                    );

            RecordMetadata metadata =
                    kafkaTemplate
                            .send(record)
                            .get(
                                    10,
                                    TimeUnit.SECONDS
                            )
                            .getRecordMetadata();

            // 3. Verify that Kafka selected the same partition.
            assertEquals(
                    expectedPartition,
                    metadata.partition(),
                    "Events using the same key must use the same partition"
            );

            // 4. Verify that Kafka assigned a later offset.
            assertTrue(
                    metadata.offset() > firstOffset,
                    "Later events must receive larger Kafka offsets"
            );
        }

        // 5. Assign the consumer directly to the partition selected by Kafka.
        TopicPartition topicPartition =
                new TopicPartition(
                        TOPIC,
                        expectedPartition
                );

        consumer.assign(
                List.of(topicPartition)
        );

        // 6. Start reading from the first test event.
        consumer.seek(
                topicPartition,
                firstOffset
        );

        // 7. Collect our test events.
        List<ConsumerRecord<String, String>> receivedRecords =
                new ArrayList<>();

        long deadline =
                System.currentTimeMillis()
                        + 15_000;

        while (
                receivedRecords.size() < EVENT_COUNT
                        && System.currentTimeMillis() < deadline
        ) {

            ConsumerRecords<String, String> records =
                    consumer.poll(
                            Duration.ofMillis(500)
                    );

            for (ConsumerRecord<String, String> record :
                    records) {

                if (
                        record.value() != null
                                && record.value()
                                .startsWith(TEST_PREFIX)
                ) {

                    receivedRecords.add(record);
                }
            }
        }

        // 8. Verify that all events were received.
        assertEquals(
                EVENT_COUNT,
                receivedRecords.size(),
                "All ordering test events must be received"
        );

        // 9. Verify that every event uses the same Kafka key.
        for (ConsumerRecord<String, String> record :
                receivedRecords) {

            assertEquals(
                    TEST_KEY,
                    record.key(),
                    "All events must use the same Kafka key"
            );
        }

        // 10. Verify that every event came from the same partition.
        for (ConsumerRecord<String, String> record :
                receivedRecords) {

            assertEquals(
                    expectedPartition,
                    record.partition(),
                    "All events must come from the same partition"
            );
        }

        // 11. Verify that Kafka preserved the publishing order.
        for (int i = 0; i < EVENT_COUNT; i++) {

            assertEquals(
                    expectedEvents.get(i),
                    receivedRecords.get(i).value(),
                    "Kafka must preserve ordering within a partition"
            );
        }

        // 12. Verify that offsets increase in the same order.
        for (int i = 1; i < receivedRecords.size(); i++) {

            assertTrue(
                    receivedRecords.get(i).offset()
                            > receivedRecords.get(i - 1).offset(),
                    "Kafka offsets must increase in event order"
            );
        }
    }
}