package com.example.ledgercore.kafka;

import com.example.ledgercore.event.EventProcessingService;
import com.example.ledgercore.event.KafkaEvent;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.outbox.DepositEventPayload;

import com.example.ledgercore.redis.AccountBalanceRedisService;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import com.example.ledgercore.repository.ProcessedEventRepository;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka Consumer Retry policy, Dead-Letter Topic (DLT) forwarding,
 * and ProcessedEvent idempotency.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=dlt-test-consumer-group-v2",
        "spring.kafka.consumer.auto-offset-reset=latest",
        "ledgercore.reconciliation.fixed-delay=300000"
})
public class KafkaConsumerRetryAndDltTest {

    private static final String MAIN_TOPIC = "ledgercore-transactions";
    private static final String DLT_TOPIC = "ledgercore-transactions.DLT";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private AccountBalanceRedisService redisService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private org.springframework.kafka.config.KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private KafkaConsumer<String, String> dltConsumer;
    private Account testAccount;
    private Customer testCustomer;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure Kafka listener container is running
        for (org.springframework.kafka.listener.MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            long deadline = System.currentTimeMillis() + 10000L;
            while (!container.isRunning() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
        }

        // Isolated DLT consumer
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        dltConsumer = new KafkaConsumer<>(props);

        // Assign to DLT topic partitions
        List<TopicPartition> partitions = List.of(
                new TopicPartition(DLT_TOPIC, 0),
                new TopicPartition(DLT_TOPIC, 1),
                new TopicPartition(DLT_TOPIC, 2)
        );
        dltConsumer.assign(partitions);
        dltConsumer.poll(Duration.ofMillis(100)); // Initialize partition assignment

        // Create test account
        testCustomer = new Customer();
        testCustomer.setCustomerName("DLT Test Customer");
        testCustomer.setCustomerAddress("100 DLT Way");
        testCustomer.setCustomerPhoneNumber("5555555555");
        testCustomer.setCustomerEmail("dlt.test." + System.nanoTime() + "@test.com");
        testCustomer = customerRepository.saveAndFlush(testCustomer);

        testAccount = new Account();
        testAccount.setAccountNumber("DLT-ACC-" + System.currentTimeMillis());
        testAccount.setBalance(new BigDecimal("500.00"));
        testAccount.setCurrency(Currency.INR);
        testAccount.setStatus(AccountStatus.ACTIVE);
        testAccount.setAccountType(AccountType.CUSTOMER);
        testAccount.setCustomer(testCustomer);
        testAccount = accountRepository.saveAndFlush(testAccount);
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        if (testAccount != null && testAccount.getAccountId() != null) {
            redisService.deleteBalance(testAccount.getAccountId());
            accountRepository.deleteById(testAccount.getAccountId());
        }
        if (testCustomer != null && testCustomer.getCustomerId() != null) {
            customerRepository.deleteById(testCustomer.getCustomerId());
        }
    }

    @Test
    void testNormalEventProcessingSuccess() throws Exception {
        Long eventId = System.currentTimeMillis() + 10000L;
        DepositEventPayload payload = new DepositEventPayload(
                1L, testAccount.getAccountId(), new BigDecimal("100.00"), Currency.INR, "Normal deposit", new BigDecimal("600.00"), 10L
        );
        KafkaEvent kafkaEvent = new KafkaEvent(
                eventId, "DEPOSIT_COMPLETED", testAccount.getAccountId(), LocalDateTime.now(), objectMapper.writeValueAsString(payload)
        );

        kafkaTemplate.send(MAIN_TOPIC, testAccount.getAccountId().toString(), objectMapper.writeValueAsString(kafkaEvent)).get(10, TimeUnit.SECONDS);

        // Poll for processing completion
        long deadline = System.currentTimeMillis() + 15000L;
        while (processedEventRepository.findByEventId(eventId).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }

        assertTrue(processedEventRepository.findByEventId(eventId).isPresent(),
                "Normal event should be saved in processed_events table");
        assertEquals(0, new BigDecimal("600.00").compareTo(redisService.getBalance(testAccount.getAccountId())),
                "Redis projection should be updated");
    }

    @Test
    void testTransientConsumerFailureRetryAndSuccess() throws Exception {
        Long eventId = System.currentTimeMillis() + 20000L;
        DepositEventPayload payload = new DepositEventPayload(
                2L, testAccount.getAccountId(), new BigDecimal("200.00"), Currency.INR, "Transient test", new BigDecimal("700.00"), 15L
        );
        KafkaEvent kafkaEvent = new KafkaEvent(
                eventId, "TRANSIENT_FAILURE", testAccount.getAccountId(), LocalDateTime.now(), objectMapper.writeValueAsString(payload)
        );

        kafkaTemplate.send(MAIN_TOPIC, testAccount.getAccountId().toString(), objectMapper.writeValueAsString(kafkaEvent)).get(10, TimeUnit.SECONDS);

        // Wait for retry completion
        long deadline = System.currentTimeMillis() + 15000L;
        while (processedEventRepository.findByEventId(eventId).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }

        assertTrue(processedEventRepository.findByEventId(eventId).isPresent(),
                "Transient failure event should succeed on retry and be recorded in processed_events");
        assertEquals(0, new BigDecimal("700.00").compareTo(redisService.getBalance(testAccount.getAccountId())),
                "Redis projection should be updated upon retry success");
    }

    @Test
    void testPermanentConsumerFailureForwardedToDLTAndNotSavedInProcessedEvents() throws Exception {
        Long eventId = System.currentTimeMillis() + 30000L;
        KafkaEvent kafkaEvent = new KafkaEvent(
                eventId, "TEST_FAILURE", testAccount.getAccountId(), LocalDateTime.now(), "{\"fail\":true}"
        );

        String messageKey = "PERM_FAIL_KEY_" + eventId;
        kafkaTemplate.send(MAIN_TOPIC, messageKey, objectMapper.writeValueAsString(kafkaEvent)).get(10, TimeUnit.SECONDS);

        // Poll DLT topic for the failed message
        List<ConsumerRecord<String, String>> dltRecords = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20000L;

        while (dltRecords.isEmpty() && System.currentTimeMillis() < deadline) {
            dltConsumer.seekToBeginning(dltConsumer.assignment());
            ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(eventId.toString())) {
                    dltRecords.add(record);
                }
            }
        }

        assertFalse(dltRecords.isEmpty(), "Permanent failure event must be forwarded to ledgercore-transactions.DLT");
        ConsumerRecord<String, String> dltRecord = dltRecords.get(0);
        assertEquals(messageKey, dltRecord.key(), "DLT record must preserve original message key");
        assertTrue(dltRecord.value().contains(eventId.toString()), "DLT record must preserve original event payload");

        // Verify DLT headers present
        assertNotNull(dltRecord.headers().lastHeader("kafka_dlt-original-topic"),
                "DLT record must preserve kafka_dlt-original-topic header");

        // Verify permanent failure is NOT in processed_events
        assertTrue(processedEventRepository.findByEventId(eventId).isEmpty(),
                "Permanently failed event must NOT be inserted into processed_events table");
    }

    @Test
    void testRedeliveredProcessedEventIsIdempotent() throws Exception {
        Long eventId = System.currentTimeMillis() + 40000L;
        DepositEventPayload payload = new DepositEventPayload(
                4L, testAccount.getAccountId(), new BigDecimal("50.00"), Currency.INR, "Idempotent test", new BigDecimal("550.00"), 20L
        );
        KafkaEvent kafkaEvent = new KafkaEvent(
                eventId, "DEPOSIT_COMPLETED", testAccount.getAccountId(), LocalDateTime.now(), objectMapper.writeValueAsString(payload)
        );

        // First execution
        eventProcessingService.process(kafkaEvent);
        assertTrue(processedEventRepository.findByEventId(eventId).isPresent());
        assertEquals(0, new BigDecimal("550.00").compareTo(redisService.getBalance(testAccount.getAccountId())));

        // Second execution (redelivery)
        eventProcessingService.process(kafkaEvent);
        assertTrue(processedEventRepository.findByEventId(eventId).isPresent(), "ProcessedEvent row should remain present");
        assertEquals(0, new BigDecimal("550.00").compareTo(redisService.getBalance(testAccount.getAccountId())),
                "Redelivered event must not corrupt balance");
    }

    @Test
    void testFailurePostRedisProjectionIsSafeOnRetry() throws Exception {
        Long eventId = System.currentTimeMillis() + 50000L;
        DepositEventPayload payload = new DepositEventPayload(
                5L, testAccount.getAccountId(), new BigDecimal("100.00"), Currency.INR, "Post-redis test", new BigDecimal("600.00"), 25L
        );
        KafkaEvent kafkaEvent = new KafkaEvent(
                eventId, "DEPOSIT_COMPLETED", testAccount.getAccountId(), LocalDateTime.now(), objectMapper.writeValueAsString(payload)
        );

        // Manually update Redis as if step 1 succeeded
        redisService.setBalanceIfVersionGreater(testAccount.getAccountId(), new BigDecimal("600.00"), 25L);

        // Execute processing (which updates Redis via Lua guard and saves ProcessedEvent)
        eventProcessingService.process(kafkaEvent);

        assertTrue(processedEventRepository.findByEventId(eventId).isPresent());
        assertEquals(0, new BigDecimal("600.00").compareTo(redisService.getBalance(testAccount.getAccountId())),
                "Redis projection remains intact at version 25");
        assertEquals(25L, redisService.getVersion(testAccount.getAccountId()));
    }
}
