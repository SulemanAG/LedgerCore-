package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.WithdrawalRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InsufficientFundsException;
import com.example.ledgercore.exception.InvalidWithdrawalException;
import com.example.ledgercore.model.*;
import com.example.ledgercore.outbox.OutboxEvent;
import com.example.ledgercore.outbox.OutboxEventStatus;
import com.example.ledgercore.outbox.WithdrawalEventPayload;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.OutboxRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.security.AccountAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service responsible for executing withdrawal operations.
 *
 * <p>
 * A withdrawal removes money from a customer account and records
 * the corresponding accounting entries.
 * </p>
 *
 * <p>
 * The transaction, ledger entries, balance updates, and transactional
 * outbox event are executed inside a single database transaction
 * to preserve atomicity.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class WithdrawalServiceImpl implements WithdrawalService {

    private static final String SYSTEM_ACCOUNT_NUMBER =
            "LC-SYSTEM-INR";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountAuthorizationService accountAuthorizationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WithdrawalServiceImpl(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountAuthorizationService accountAuthorizationService,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountAuthorizationService = accountAuthorizationService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawalRequest request) {

        // 1. Validate the withdrawal request
        validateWithdrawalRequest(request);

        // 2. Lock the idempotency key to prevent concurrent withdrawal requests
        transactionRepository.lockIdempotencyKey(
                request.getIdempotencyKey()
        );

        // 3. Check if a transaction with this idempotency key already exists
        Optional<Transaction> existingTransaction =
                transactionRepository.findByIdempotencyKey(
                        request.getIdempotencyKey()
                );

        if (existingTransaction.isPresent()) {

            Transaction transaction = existingTransaction.get();

            boolean matchesAmount =
                    transaction.getAmount()
                            .compareTo(request.getAmount()) == 0;

            boolean matchesCurrency =
                    transaction.getCurrency() == request.getCurrency();

            boolean matchesAccount = true;

            if (transaction.getLedgerEntries() != null
                    && !transaction.getLedgerEntries().isEmpty()) {

                matchesAccount = transaction.getLedgerEntries()
                        .stream()
                        .anyMatch(entry ->
                                entry.getEntryType() == LedgerEntryType.DEBIT
                                        && entry.getAccount()
                                        .getAccountId()
                                        .equals(request.getAccountId())
                        );
            }

            if (!matchesAmount
                    || !matchesCurrency
                    || !matchesAccount) {

                throw new InvalidWithdrawalException(
                        "Idempotency key was already used for a different withdrawal request"
                );
            }

            return new TransactionResponse(
                    transaction.getTransactionId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getStatus(),
                    transaction.getCreatedAt(),
                    transaction.getReference()
            );
        }

        // 4. Load the customer account
        Account customerAccount = accountRepository
                .findById(request.getAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.getAccountId()
                        )
                );

        // 5. Verify that the authenticated user owns the account
        if (!accountAuthorizationService.isOwner(customerAccount)) {

            throw new AccessDeniedException(
                    "You are not authorized to withdraw money from this account"
            );
        }

        // 6. Verify that the account is active
        if (customerAccount.getStatus() != AccountStatus.ACTIVE) {

            throw new InvalidWithdrawalException(
                    "Account " + customerAccount.getAccountNumber()
                            + " is not ACTIVE"
            );
        }

        // 7. Validate currency
        if (customerAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidWithdrawalException(
                    "Withdrawal currency does not match account currency"
            );
        }

        // 8. Check for sufficient funds
        if (customerAccount.getBalance()
                .compareTo(request.getAmount()) < 0) {

            throw new InsufficientFundsException(
                    "Insufficient funds in the account"
            );
        }

        // 9. Check for the SYSTEM account
        Account systemAccount = accountRepository
                .findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "SYSTEM account not found!"
                        )
                );

        // 10. Verify that the SYSTEM account is correctly configured
        if (systemAccount.getAccountType() != AccountType.SYSTEM) {

            throw new IllegalStateException(
                    "Configured SYSTEM account is not a SYSTEM account"
            );
        }

        if (systemAccount.getStatus() != AccountStatus.ACTIVE) {

            throw new IllegalStateException(
                    "SYSTEM account is not active"
            );
        }

        if (systemAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidWithdrawalException(
                    "SYSTEM account currency does not match withdrawal currency"
            );
        }

        // 11. Create the withdrawal transaction
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());
        transaction.setIdempotencyKey(request.getIdempotencyKey());

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        // 12. Create DEBIT ledger entry for the customer account
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(customerAccount);

        ledgerEntryRepository.save(debitEntry);

        // 13. Create CREDIT ledger entry for the SYSTEM account
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(systemAccount);

        ledgerEntryRepository.save(creditEntry);

        // 14. Reduce the customer account balance
        customerAccount.setBalance(
                customerAccount.getBalance()
                        .subtract(request.getAmount())
        );

        // 15. Increase the SYSTEM account balance
        systemAccount.setBalance(
                systemAccount.getBalance()
                        .add(request.getAmount())
        );

        // 16. Save both updated account balances
        accountRepository.save(customerAccount);
        accountRepository.save(systemAccount);

        /*
         * Step 17:
         * Create the transactional outbox event.
         *
         * This event is persisted inside the same database transaction
         * as the withdrawal, ledger entries, and balance updates.
         */
        WithdrawalEventPayload eventPayload =
                new WithdrawalEventPayload(
                        savedTransaction.getTransactionId(),
                        customerAccount.getAccountId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getReference()
                );

        try {

            String payload =
                    objectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    "WITHDRAWAL_COMPLETED",
                    savedTransaction.getTransactionId(),
                    payload,
                    OutboxEventStatus.PENDING,
                    LocalDateTime.now()
            );

            outboxRepository.save(outboxEvent);

        } catch (JacksonException e) {

            /*
             * Throwing the exception from this @Transactional method
             * causes the complete database transaction to roll back.
             */
            throw new IllegalStateException(
                    "Failed to create withdrawal outbox event",
                    e
            );
        }

        // 18. Return the completed withdrawal transaction
        return new TransactionResponse(
                savedTransaction.getTransactionId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus(),
                savedTransaction.getCreatedAt(),
                savedTransaction.getReference()
        );
    }

    /**
     * Validates withdrawal request fields that do not require
     * database access.
     *
     * @param request withdrawal request to validate
     */
    private void validateWithdrawalRequest(WithdrawalRequest request) {

        if (request == null) {

            throw new InvalidWithdrawalException(
                    "Withdrawal request cannot be null"
            );
        }

        if (request.getAccountId() == null) {

            throw new InvalidWithdrawalException(
                    "Account ID cannot be null"
            );
        }

        if (request.getAmount() == null) {

            throw new InvalidWithdrawalException(
                    "Withdrawal amount cannot be null"
            );
        }

        if (request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidWithdrawalException(
                    "Withdrawal amount must be greater than zero"
            );
        }

        if (request.getCurrency() == null) {

            throw new InvalidWithdrawalException(
                    "Withdrawal currency cannot be null"
            );
        }

        if (request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank()) {

            throw new InvalidWithdrawalException(
                    "Idempotency key cannot be blank"
            );
        }

        if (request.getIdempotencyKey().length() > 100) {

            throw new InvalidWithdrawalException(
                    "Idempotency key cannot exceed 100 characters"
            );
        }
    }
}