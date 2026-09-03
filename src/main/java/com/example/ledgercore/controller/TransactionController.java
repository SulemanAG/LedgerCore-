package com.example.ledgercore.controller;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.request.WithdrawalRequest;
import com.example.ledgercore.dto.response.LedgerEntryResponse;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.service.TransactionService;
import com.example.ledgercore.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for financial transactions.
 *
 * <p>This controller exposes APIs for transferring money between
 * LedgerCore accounts.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final WithdrawalService withdrawalService;

    public TransactionController(TransactionService transactionService, WithdrawalService withdrawalService) {
        this.transactionService = transactionService;
        this.withdrawalService=withdrawalService;
    }

    /**
     * Transfers money from one account to another.
     *
     * @param request transfer details
     * @return completed transaction
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request) {

        TransactionResponse response =
                transactionService.transfer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Retrieves all transactions associated with an account.
     *
     * <p>
     * Access is restricted to the authenticated owner of the account.
     * </p>
     *
     * @param accountId ID of the account
     * @return list of transactions associated with the account
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<List<TransactionResponse>> getAccountTransactions(
            @PathVariable Long accountId) {

        return ResponseEntity.ok(
                transactionService.getTransactionsByAccount(accountId)
        );
    }

    /**
     * Retrieves a transaction by its ID.
     *
     * <p>
     * Access is restricted to authenticated users who own
     * at least one account involved in the transaction.
     * </p>
     *
     * @param transactionId ID of the transaction
     * @return transaction response
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @PathVariable Long transactionId) {

        return ResponseEntity.ok(
                transactionService.getTransactionById(transactionId)
        );
    }


    /**
     * Retrieves the ledger entries associated with a transaction.
     *
     * @param transactionId ID of the transaction
     * @return list of ledger entries for the transaction
     */
    @GetMapping("/{transactionId}/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerEntries(
            @PathVariable Long transactionId) {

        return ResponseEntity.ok(
                transactionService.getLedgerEntriesByTransaction(
                        transactionId
                )
        );
    }

    /**
     * Withdraws money from a customer account.
     *
     * @param request withdrawal details
     * @return completed withdrawal transaction
     */
    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawalRequest request) {

        TransactionResponse response =
                withdrawalService.withdraw(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}