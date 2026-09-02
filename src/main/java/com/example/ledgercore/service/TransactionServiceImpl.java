package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.exception.AccountNotFoundException;
import com.example.ledgercore.exception.InsufficientFundsException;
import com.example.ledgercore.exception.InvalidTransferException;
import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.LedgerEntry;
import com.example.ledgercore.model.LedgerEntryType;
import com.example.ledgercore.model.Transaction;
import com.example.ledgercore.model.TransactionStatus;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.LedgerEntryRepository;
import com.example.ledgercore.repository.TransactionRepository;
import com.example.ledgercore.security.AccountAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * </ul>
 *
 * <p>
 * All of these operations are executed inside one database transaction.
 * If any operation fails, the complete operation is rolled back.
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

    public TransactionServiceImpl(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AccountAuthorizationService accountAuthorizationService
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountAuthorizationService = accountAuthorizationService;
    }

    /**
     * Transfers money from the source account to the destination account.
     *
     * <p>
     * The entire operation is atomic. Either every database change succeeds
     * and is committed, or all changes are rolled back.
     * </p>
     *
     * @param request transfer details
     * @return completed transaction response
     */
    @Override
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {

        //1. BASIC REQUEST VALIDATION
        validateTransferRequest(request);


        // 2. LOAD SOURCE ACCOUNT
        Account sourceAccount = accountRepository
                .findById(request.getSourceAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.getSourceAccountId()
                        )
                );




         //3. VERIFY SOURCE ACCOUNT OWNERSHIP
        if (!accountAuthorizationService.isOwner(sourceAccount)) {

            throw new AccessDeniedException(
                    "You are not authorized to transfer money from this account"
            );
        }


        // 4. LOAD DESTINATION ACCOUNT


        Account destinationAccount = accountRepository
                .findById(request.getDestinationAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(
                                request.getDestinationAccountId()
                        )
                );


       //5. VALIDATE ACCOUNT STATUS
        validateAccountStatus(sourceAccount);
        validateAccountStatus(destinationAccount);


       // 6. VALIDATE CURRENCY
        validateCurrency(sourceAccount, destinationAccount, request);


      //7. CHECK SUFFICIENT BALANCE
        if (sourceAccount.getBalance()
                .compareTo(request.getAmount()) < 0) {

            throw new InsufficientFundsException(
                    "Insufficient funds in source account"
            );
        }


        //8. CREATE TRANSACTION RECORD
        Transaction transaction = new Transaction();

        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setReference(request.getReference());

        Transaction savedTransaction =
                transactionRepository.save(transaction);


        //9. CREATE DEBIT LEDGER ENTRY


        LedgerEntry debitEntry = new LedgerEntry();

        debitEntry.setAmount(request.getAmount());
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(sourceAccount);

        ledgerEntryRepository.save(debitEntry);


        // 10. CREATE CREDIT LEDGER ENTRY

        LedgerEntry creditEntry = new LedgerEntry();

        creditEntry.setAmount(request.getAmount());
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(destinationAccount);

        ledgerEntryRepository.save(creditEntry);


        // 11. UPDATE SOURCE BALANCE
        sourceAccount.setBalance(
                sourceAccount.getBalance()
                        .subtract(request.getAmount())
        );


       // 12. UPDATE DESTINATION BALANCE
        destinationAccount.setBalance(
                destinationAccount.getBalance()
                        .add(request.getAmount())
        );


        //13. SAVE BOTH ACCOUNT BALANCES
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);


        // 14. RETURN RESPONSE
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
     * Performs validation that does not require database access.
     *
     * @param request transfer request
     */
    private void validateTransferRequest(TransferRequest request) {

        if (request == null) {

            throw new InvalidTransferException(
                    "Transfer request cannot be null"
            );
        }

        if (request.getSourceAccountId() == null) {

            throw new InvalidTransferException(
                    "Source account ID cannot be null"
            );
        }

        if (request.getDestinationAccountId() == null) {

            throw new InvalidTransferException(
                    "Destination account ID cannot be null"
            );
        }

        if (request.getSourceAccountId()
                .equals(request.getDestinationAccountId())) {

            throw new InvalidTransferException(
                    "Source and destination accounts cannot be the same"
            );
        }

        if (request.getAmount() == null) {

            throw new InvalidTransferException(
                    "Transfer amount cannot be null"
            );
        }

        if (request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new InvalidTransferException(
                    "Transfer amount must be greater than zero"
            );
        }

        if (request.getCurrency() == null) {

            throw new InvalidTransferException(
                    "Transfer currency cannot be null"
            );
        }
    }


    /**
     * Ensures both accounts are active.
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

        if (sourceAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Transfer currency does not match source account currency"
            );
        }

        if (destinationAccount.getCurrency() != request.getCurrency()) {

            throw new InvalidTransferException(
                    "Transfer currency does not match destination account currency"
            );
        }
    }
}