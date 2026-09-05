package com.example.ledgercore.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Provides read-side account balance projections in Redis.
 *
 * Redis is used as a fast projection layer.
 * PostgreSQL remains the source of truth for financial state.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class AccountBalanceRedisService {

    private static final String KEY_PREFIX = "ledgercore:account:";

    private final StringRedisTemplate redisTemplate;

    public AccountBalanceRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Builds the Redis key used for an account balance.
     *
     * @param accountId account identifier
     * @return Redis key
     */
    private String buildBalanceKey(Long accountId) {
        return KEY_PREFIX + accountId + ":balance";
    }

    /**
     * Stores the current account balance in Redis.
     *
     * @param accountId account identifier
     * @param balance current account balance
     */
    public void setBalance(Long accountId, BigDecimal balance) {

        String key = buildBalanceKey(accountId);

        redisTemplate.opsForValue().set(
                key,
                balance.toPlainString()
        );
    }

    /**
     * Retrieves an account balance from Redis.
     *
     * @param accountId account identifier
     * @return balance if present, otherwise null
     */
    public BigDecimal getBalance(Long accountId) {

        String key = buildBalanceKey(accountId);

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        return new BigDecimal(value);
    }

    /**
     * Removes the account balance projection from Redis.
     *
     * @param accountId account identifier
     */
    public void deleteBalance(Long accountId) {

        String key = buildBalanceKey(accountId);

        redisTemplate.delete(key);
    }
}