package com.example.ledgercore.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final String LUA_VERSION_GUARD_SCRIPT = """
            local current_version = redis.call('GET', KEYS[2])
            if current_version and tonumber(ARGV[2]) <= tonumber(current_version) then
                return 0
            else
                redis.call('SET', KEYS[1], ARGV[1])
                redis.call('SET', KEYS[2], ARGV[2])
                return 1
            end
            """;

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
     * Builds the Redis key used for an account version.
     *
     * @param accountId account identifier
     * @return Redis version key
     */
    private String buildVersionKey(Long accountId) {
        return KEY_PREFIX + accountId + ":version";
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
     * Atomically sets the account balance and version in Redis ONLY IF the supplied
     * version is strictly greater than the current projected version in Redis.
     *
     * @param accountId account identifier
     * @param balance current account balance
     * @param version monotonic account version
     * @return true if updated; false if rejected as a stale event
     */
    public boolean setBalanceIfVersionGreater(Long accountId, BigDecimal balance, Long version) {

        if (version == null) {
            setBalance(accountId, balance);
            return true;
        }

        String balanceKey = buildBalanceKey(accountId);
        String versionKey = buildVersionKey(accountId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_VERSION_GUARD_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                List.of(balanceKey, versionKey),
                balance.toPlainString(),
                version.toString()
        );

        boolean updated = (result != null && result == 1L);
        if (!updated) {
            System.out.println("LUA VERSION GUARD: Ignored stale/out-of-order Redis projection update for account "
                    + accountId + " [event version=" + version + "]");
        }
        return updated;
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
     * Retrieves the projected account version from Redis.
     *
     * @param accountId account identifier
     * @return version if present, otherwise null
     */
    public Long getVersion(Long accountId) {
        String key = buildVersionKey(accountId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Removes both the account balance and version projection keys from Redis.
     *
     * @param accountId account identifier
     */
    public void deleteBalance(Long accountId) {

        String balanceKey = buildBalanceKey(accountId);
        String versionKey = buildVersionKey(accountId);

        redisTemplate.delete(List.of(balanceKey, versionKey));
    }


    public Set<Long> findProjectedAccountIds() {
        Set<Long> accountIds = new HashSet<>();

        Set<String> keys = redisTemplate.keys(
                KEY_PREFIX + "*:balance"
        );

        if (keys == null) {
            return accountIds;
        }

        for (String key : keys) {
            String accountIdPart = key.substring(
                    KEY_PREFIX.length(),
                    key.length() - ":balance".length()
            );

            try {
                accountIds.add(Long.valueOf(accountIdPart));
            } catch (NumberFormatException ignored) {
                // Ignore malformed Redis keys.
            }
        }

        return accountIds;
    }
}