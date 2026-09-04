package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InvalidTransferException;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.LedgerEntryType;
import com.example.ledgercore.model.Transaction;
import com.example.ledgercore.model.TransactionStatus;
import com.example.ledgercore.outbox.DepositEventPayload;
import com.example.ledgercore.outbox.OutboxEvent;
import com.example.ledgercore.outbox.OutboxEventStatus;
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

/**
 * Service implementation responsible for deposit operations.
 *
 * <p>
 * A deposit is represented as a double-entry accounting operation.
 * The customer account receives a CREDIT entry while the internal
 * SYSTEM account receives the corresponding DEBIT entry.
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
public class DepositServiceImpl implements DepositService {

    private static final String SYSTEM_ACCOUNT_NUMBER =
            "LC-SYSTEM-INR";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountAuthorizationService accountAuthorizationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DepositServiceImpl(
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
    public TransactionResponse deposit(DepositRequest request) {

        validateDepositRequest(request);

        /*
         * Step 1:
         * Lock the idempotency key to prevent concurrent deposit requests
         * with the same key from creating duplicate transactions.
         */
        transactionRepository.lockIdempotencyKey(
                request.getIdempotencyKey()
        );

        /*
         * Step 2:
         * Check if a transaction with this idempotency key
         * already exists.
         */
        java.util.Optional<Transaction> existingTransaction =
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
                                entry.getEntryType() == LedgerEntryType.CREDIT
                                        && entry.getAccount()
                                        .getAccountId()
                                        .equals(request.getAccountId())
                        );
            }

            /*
             * Same idempotency key with different business data
             * must be rejected.
             */
            if (!matchesAmount
                    || !matchesCurrency
                    || !matchesAccount) {

                throw new InvalidTransferException(
                        "Idempotency key was already used for a different deposit request"
                );
            }

            /*
             * Same request + same idempotency key:
             * return the original transaction.
             */
            return new TransactionResponse(
                    transaction.getTransactionId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getStatus(),
                    transaction.getCreatedAt(),
                    transaction.getReference()
            );
        }

        /*
         * Step 3:
         * Find the customer account receiving the money.
         */
        Account customerAccount = accountRepository
                .findById(request.getAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.getAccountId()
                        )
                );

        /*
         * Step 4:
         * Make sure the authenticated user owns the account.
         */
        if (!accountAuthorizationService.isOwner(customerAccount)) {

            throw new AccessDeniedException(
                    "You are not authorized to deposit into this account"
            );
        }

        /*
         * Step 5:
         * The customer account must be ACTIVE.
         */
        validateAccountStatus(customerAccount);

        /*
         * Step 6:
         * The requested currency must match the account currency.
         */
        validateCurrency(customerAccount, request);

        /*
         * Step 7:
         * Find LedgerCore's internal SYSTEM account.
         */
        Account systemAccount = accountRepository
                .findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "LedgerCore SYSTEM account does not exist"
                        )
                );

        /*
         * Step 8:
         * Make sure the account found is actually a SYSTEM account.
         */
        if (systemAccount.getAccountType() != AccountType.SYSTEM) {

            throw new IllegalStateException(
                    "Configured SYSTEM account is not a SYSTEM account"
            );
        }

        /*
         * Step 9:
         * Make sure the SYSTEM account is active.
         */
        validateAccountStatus(systemAccount);

        /*
         * Step 10:
         * The SYSTEM account must use the same currency.
         */
        if (systemAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Deposit currency does not match SYSTEM account currency"
            );
        }

        /*
         * Step 11:
         * Create the business transaction.
         */
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());
        transaction.setIdempotencyKey(request.getIdempotencyKey());

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        /*
         * Step 12:
         * Create the DEBIT ledger entry for the SYSTEM account.
         *
         * SYSTEM account:
         *     DEBIT  = money leaving the system source.
         */
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(systemAccount);

        ledgerEntryRepository.save(debitEntry);

        /*
         * Step 13:
         * Create the CREDIT ledger entry for the customer account.
         *
         * Customer account:
         *     CREDIT = money received by the customer.
         */
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(customerAccount);

        ledgerEntryRepository.save(creditEntry);

        /*
         * Step 14:
         * Update the current account balances.
         *
         * The ledger entries represent the accounting history.
         * The account balance represents the current state.
         */
        systemAccount.setBalance(
                systemAccount.getBalance()
                        .add(request.getAmount())
        );

        customerAccount.setBalance(
                customerAccount.getBalance()
                        .add(request.getAmount())
        );

        accountRepository.save(systemAccount);
        accountRepository.save(customerAccount);

        /*
         * Step 15:
         * Create the transactional outbox event.
         *
         * This record is saved inside the SAME database transaction
         * as the transaction, ledger entries, and balance updates.
         *
         * The event initially has PENDING status.
         */
        DepositEventPayload eventPayload =
                new DepositEventPayload(
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
                    "DEPOSIT_COMPLETED",
                    savedTransaction.getTransactionId(),
                    payload,
                    OutboxEventStatus.PENDING,
                    LocalDateTime.now()
            );

            outboxRepository.save(outboxEvent);

        } catch (JacksonException e) {

            /*
             * Because this exception is thrown inside the
             * @Transactional method, the database transaction
             * will be rolled back.
             */
            throw new IllegalStateException(
                    "Failed to create deposit outbox event",
                    e
            );
        }

        /*
         * Step 16:
         * Return the created transaction.
         */
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
     * Validates the incoming deposit request.
     *
     * @param request deposit request
     */
    private void validateDepositRequest(DepositRequest request) {

        if (request == null) {

            throw new InvalidTransferException(
                    "Deposit request cannot be null"
            );
        }

        if (request.getAccountId() == null) {

            throw new InvalidTransferException(
                    "Account ID cannot be null"
            );
        }

        if (request.getAmount() == null) {

            throw new InvalidTransferException(
                    "Deposit amount cannot be null"
            );
        }

        if (request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidTransferException(
                    "Deposit amount must be greater than zero"
            );
        }

        if (request.getCurrency() == null) {

            throw new InvalidTransferException(
                    "Deposit currency cannot be null"
            );
        }

        if (request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank()) {

            throw new InvalidTransferException(
                    "Idempotency key cannot be blank"
            );
        }

        if (request.getIdempotencyKey().length() > 100) {

            throw new InvalidTransferException(
                    "Idempotency key cannot exceed 100 characters"
            );
        }
    }

    /**
     * Validates that the account is active.
     *
     * @param account account being validated
     */
    private void validateAccountStatus(Account account) {

        if (account.getStatus() != AccountStatus.ACTIVE) {

            throw new InvalidTransferException(
                    "Account " + account.getAccountNumber()
                            + " is not active"
            );
        }
    }

    /**
     * Validates that the deposit currency matches
     * the customer account currency.
     *
     * @param customerAccount customer account
     * @param request deposit request
     */
    private void validateCurrency(
            Account customerAccount,
            DepositRequest request
    ) {

        if (customerAccount.getCurrency()
                != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Deposit currency does not match account currency"
            );
        }
    }
}