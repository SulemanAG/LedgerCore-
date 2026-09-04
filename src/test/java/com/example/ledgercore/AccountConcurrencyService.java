package com.example.ledgercore;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Provides transactional operations used by concurrency integration tests.
 *
 * <p>Each invocation of {@link #updateAccountBalance(Long, BigDecimal)}
 * executes inside its own Spring transaction.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class AccountConcurrencyService {

    private final AccountRepository accountRepository;

    public AccountConcurrencyService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Loads an account, changes its balance, and saves it.
     *
     * <p>The {@code @Transactional} annotation ensures that each invocation
     * runs inside an independent database transaction when called from
     * separate worker threads.</p>
     *
     * @param accountId account to update
     * @param newBalance new balance value
     */
    @Transactional
    public void updateAccountBalance(
            Long accountId,
            BigDecimal newBalance
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow();

        account.setBalance(newBalance);

        accountRepository.save(account);
    }
}