package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.LedgerEntryResponse;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InsufficientFundsException;
import com.example.ledgercore.exception.InvalidTransferException;
import com.example.ledgercore.exception.TransactionNotFoundException;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.LedgerEntryType;
import com.example.ledgercore.model.Transaction;
import com.example.ledgercore.model.TransactionStatus;
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
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for executing financial transactions.
 *
 * <p>
 * A transfer consists of:
 * </p>
 *
 * <ul>
 *     <li>One transaction record</li>
 *     <li>One DEBIT ledger entry</li>
 *     <li>One CREDIT ledger entry</li>
 *     <li>A decrease in the source account balance</li>
 *     <li>An increase in the destination account balance</li>
 *     <li>One transactional outbox event</li>
 * </ul>
 *
 * <p>
 * All database operations are executed inside one transaction.
 * If any operation fails, the complete operation is rolled back.
 * </p>
 *
 * <p>
 * Transfer idempotency is protected using a database-level advisory lock
 * based on the supplied idempotency key. This prevents concurrent requests
 * using the same key from processing the financial operation more than once.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountAuthorizationService accountAuthorizationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionServiceImpl(
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

    /**
     * Transfers money from the source account to the destination account.
     *
     * <p>
     * The entire operation is atomic. Either every database change succeeds
     * and is committed, or all changes are rolled back.
     * </p>
     *
     * <p>
     * The idempotency key is locked at the database level before checking
     * whether the transaction has already been processed. This ensures that
     * concurrent requests using the same idempotency key are serialized.
     * </p>
     *
     * <p>
     * The financial transaction and its outbox event are persisted inside
     * the same PostgreSQL transaction.
     * </p>
     *
     * @param request transfer details
     * @return completed transaction response
     */
    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {

        // 1. BASIC REQUEST VALIDATION
        validateTransferRequest(request);

        // 2. LOCK IDEMPOTENCY KEY
        transactionRepository.lockIdempotencyKey(
                request.getIdempotencyKey()
        );

        // 3. CHECK IDEMPOTENCY KEY
        Optional<Transaction> existingTransaction =
                transactionRepository.findByIdempotencyKey(
                        request.getIdempotencyKey()
                );

        if (existingTransaction.isPresent()) {

            Transaction transaction = existingTransaction.get();

            boolean matchesAmount =
                    transaction.getAmount().compareTo(request.getAmount()) == 0;

            boolean matchesCurrency =
                    transaction.getCurrency() == request.getCurrency();

            boolean matchesAccounts = true;

            if (transaction.getLedgerEntries() != null
                    && !transaction.getLedgerEntries().isEmpty()) {

                boolean debitMatches =
                        transaction.getLedgerEntries()
                                .stream()
                                .anyMatch(entry ->
                                        entry.getEntryType() == LedgerEntryType.DEBIT
                                                && entry.getAccount()
                                                .getAccountId()
                                                .equals(request.getSourceAccountId())
                                );

                boolean creditMatches =
                        transaction.getLedgerEntries()
                                .stream()
                                .anyMatch(entry ->
                                        entry.getEntryType() == LedgerEntryType.CREDIT
                                                && entry.getAccount()
                                                .getAccountId()
                                                .equals(request.getDestinationAccountId())
                                );

                matchesAccounts = debitMatches && creditMatches;
            }

            if (!matchesAmount
                    || !matchesCurrency
                    || !matchesAccounts) {

                throw new InvalidTransferException(
                        "Idempotency key was already used for a different transfer request"
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

        // 4. LOAD SOURCE ACCOUNT
        Account sourceAccount =
                accountRepository
                        .findById(request.getSourceAccountId())
                        .orElseThrow(() ->
                                new AccountNotFoundException(
                                        request.getSourceAccountId()
                                )
                        );

        // 5. VERIFY SOURCE ACCOUNT OWNERSHIP
        if (!accountAuthorizationService.isOwner(sourceAccount)) {

            throw new AccessDeniedException(
                    "You are not authorized to transfer money from this account"
            );
        }

        // 6. LOAD DESTINATION ACCOUNT
        Account destinationAccount =
                accountRepository
                        .findById(request.getDestinationAccountId())
                        .orElseThrow(() ->
                                new AccountNotFoundException(
                                        request.getDestinationAccountId()
                                )
                        );

        // 7. VALIDATE ACCOUNT STATUS
        validateAccountStatus(sourceAccount);
        validateAccountStatus(destinationAccount);

        // 8. VALIDATE CURRENCY
        validateCurrency(
                sourceAccount,
                destinationAccount,
                request
        );

        // 9. CHECK SUFFICIENT BALANCE
        if (sourceAccount.getBalance()
                .compareTo(request.getAmount()) < 0) {

            throw new InsufficientFundsException(
                    "Insufficient funds in source account"
            );
        }

        // 10. CREATE TRANSACTION RECORD
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());

        // 11. SET IDEMPOTENCY KEY
        transaction.setIdempotencyKey(
                request.getIdempotencyKey()
        );

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        // 12. CREATE DEBIT LEDGER ENTRY
        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(sourceAccount);

        ledgerEntryRepository.save(debitEntry);

        // 13. CREATE CREDIT LEDGER ENTRY
        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(destinationAccount);

        ledgerEntryRepository.save(creditEntry);

        // 14. UPDATE SOURCE BALANCE
        sourceAccount.setBalance(
                sourceAccount.getBalance()
                        .subtract(request.getAmount())
        );

        // 15. UPDATE DESTINATION BALANCE
        destinationAccount.setBalance(
                destinationAccount.getBalance()
                        .add(request.getAmount())
        );

        // 16. SAVE BOTH ACCOUNT BALANCES
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // 17. CREATE TRANSFER EVENT PAYLOAD
        TransferEventPayload eventPayload =
                new TransferEventPayload(
                        savedTransaction.getTransactionId(),
                        sourceAccount.getAccountId(),
                        destinationAccount.getAccountId(),
                        savedTransaction.getAmount(),
                        savedTransaction.getCurrency(),
                        savedTransaction.getReference()
                );

        // 18. SERIALIZE EVENT PAYLOAD
        String payload;

        try {

            payload = objectMapper.writeValueAsString(eventPayload);

        } catch (JacksonException exception) {

            throw new IllegalStateException(
                    "Failed to serialize transfer outbox event",
                    exception
            );
        }

        // 19. CREATE OUTBOX EVENT
        OutboxEvent outboxEvent =
                new OutboxEvent(
                        "TRANSFER_COMPLETED",
                        savedTransaction.getTransactionId(),
                        payload,
                        OutboxEventStatus.PENDING,
                        LocalDateTime.now()
                );

        // 20. SAVE OUTBOX EVENT
        outboxRepository.save(outboxEvent);

        // 21. RETURN RESPONSE
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
     * Payload used by the TRANSFER_COMPLETED outbox event.
     *
     * <p>
     * This record is deliberately separate from the Transaction entity.
     * The event contract should not be tightly coupled to the JPA model.
     * </p>
     */
    private record TransferEventPayload(
            Long transactionId,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            Currency currency,
            String reference
    ) {
    }

    /**
     * Performs validation that does not require database access.
     *
     * @param request transfer request
     */
    private void validateTransferRequest(TransferRequest request) {

        // 1. CHECK REQUEST
        if (request == null) {

            throw new InvalidTransferException(
                    "Transfer request cannot be null"
            );
        }

        // 2. CHECK SOURCE ACCOUNT
        if (request.getSourceAccountId() == null) {

            throw new InvalidTransferException(
                    "Source account ID cannot be null"
            );
        }

        // 3. CHECK DESTINATION ACCOUNT
        if (request.getDestinationAccountId() == null) {

            throw new InvalidTransferException(
                    "Destination account ID cannot be null"
            );
        }

        // 4. CHECK SAME ACCOUNT TRANSFER
        if (request.getSourceAccountId()
                .equals(request.getDestinationAccountId())) {

            throw new InvalidTransferException(
                    "Source and destination accounts cannot be the same"
            );
        }

        // 5. CHECK AMOUNT
        if (request.getAmount() == null) {

            throw new InvalidTransferException(
                    "Transfer amount cannot be null"
            );
        }

        // 6. CHECK POSITIVE AMOUNT
        if (request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidTransferException(
                    "Transfer amount must be greater than zero"
            );
        }

        // 7. CHECK CURRENCY
        if (request.getCurrency() == null) {

            throw new InvalidTransferException(
                    "Transfer currency cannot be null"
            );
        }

        // 8. CHECK IDEMPOTENCY KEY
        if (request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank()) {

            throw new InvalidTransferException(
                    "Idempotency key cannot be blank"
            );
        }

        // 9. CHECK IDEMPOTENCY KEY LENGTH
        if (request.getIdempotencyKey().length() > 100) {

            throw new InvalidTransferException(
                    "Idempotency key cannot exceed 100 characters"
            );
        }
    }

    /**
     * Ensures the account is active.
     *
     * @param account account to validate
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
     * Ensures the requested currency matches both accounts.
     *
     * @param sourceAccount source account
     * @param destinationAccount destination account
     * @param request transfer request
     */
    private void validateCurrency(
            Account sourceAccount,
            Account destinationAccount,
            TransferRequest request
    ) {

        // 1. CHECK SOURCE ACCOUNT CURRENCY
        if (sourceAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Transfer currency does not match source account currency"
            );
        }

        // 2. CHECK DESTINATION ACCOUNT CURRENCY
        if (destinationAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Transfer currency does not match destination account currency"
            );
        }
    }

    /**
     * Retrieves all transactions associated with an account owned
     * by the authenticated user.
     *
     * @param accountId account ID
     * @return list of transaction responses
     */
    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(Long accountId) {

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new AccountNotFoundException(accountId)
                        );

        if (!accountAuthorizationService.isOwner(account)) {

            throw new AccessDeniedException(
                    "You are not authorized to access this account's transactions"
            );
        }

        return transactionRepository
                .findDistinctByLedgerEntriesAccountAccountId(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves a transaction by ID after verifying account ownership.
     *
     * @param transactionId transaction ID
     * @return transaction response
     */
    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long transactionId) {

        Transaction transaction =
                transactionRepository.findById(transactionId)
                        .orElseThrow(() ->
                                new TransactionNotFoundException(transactionId)
                        );

        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(transactionId);

        boolean authorized =
                ledgerEntries.stream()
                        .anyMatch(entry ->
                                accountAuthorizationService.isOwner(
                                        entry.getAccount()
                                )
                        );

        if (!authorized) {

            throw new AccessDeniedException(
                    "You are not authorized to access this transaction"
            );
        }

        return mapToResponse(transaction);
    }

    /**
     * Retrieves all ledger entries associated with a transaction.
     *
     * @param transactionId ID of the transaction
     * @return list of ledger entry responses
     */
    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedgerEntriesByTransaction(
            Long transactionId
    ) {

        // 1. CHECK TRANSACTION EXISTS
        transactionRepository.findById(transactionId)
                .orElseThrow(
                        () -> new TransactionNotFoundException(transactionId)
                );

        // 2. RETRIEVE LEDGER ENTRIES
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByTransactionTransactionId(transactionId);

        // 3. CHECK ACCOUNT OWNERSHIP
        boolean authorized =
                ledgerEntries.stream()
                        .anyMatch(entry ->
                                accountAuthorizationService.isOwner(
                                        entry.getAccount()
                                )
                        );

        // 4. DENY UNAUTHORIZED ACCESS
        if (!authorized) {

            throw new AccessDeniedException(
                    "You are not authorized to access this transactions's ledger"
            );
        }

        // 5. CONVERT LEDGER ENTRIES TO RESPONSE
        return ledgerEntries.stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.getLedgerEntryId(),
                        entry.getAmount(),
                        entry.getEntryType(),
                        entry.getAccount().getAccountId()
                ))
                .toList();
    }

    /**
     * Converts a Transaction entity into a TransactionResponse DTO.
     *
     * @param transaction transaction entity
     * @return transaction response
     */
    private TransactionResponse mapToResponse(Transaction transaction) {

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getReference()
        );
    }
}