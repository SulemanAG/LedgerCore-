package com.example.ledgercore.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Redis account balance projection.
 */
@SpringBootTest
class AccountBalanceRedisServiceTest {

    @Autowired
    private AccountBalanceRedisService redisService;

    @Test
    void shouldStoreAndRetrieveAccountBalance() {

        // 1. Given
        Long accountId = 999999L;
        BigDecimal balance = new BigDecimal("12500.50");

        try {
            // 2. Store balance
            redisService.setBalance(accountId, balance);

            // 3. Retrieve balance
            BigDecimal retrievedBalance =
                    redisService.getBalance(accountId);

            // 4. Verify
            assertNotNull(retrievedBalance);

            assertEquals(
                    0,
                    balance.compareTo(retrievedBalance)
            );
        } finally {
            // 5. Cleanup
            redisService.deleteBalance(accountId);

            // 6. Verify cleanup
            assertNull(
                    redisService.getBalance(accountId)
            );
        }
    }
}