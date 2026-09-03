package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;
import com.example.ledgercore.dto.response.LedgerEntryResponse;

import java.util.List;

public interface TransactionService {

    /**
     * Transfers money from one account to another.
     *
     * @param request transfer details
     * @return completed transaction response
     */
    TransactionResponse transfer(TransferRequest request);

    /**
     * Retrieves the transaction history for an account.
     *
     * @param accountId ID of the account
     * @return list of transactions associated with the account
     */
    List<TransactionResponse> getTransactionsByAccount(Long accountId);

    /**
     * Retrieves a transaction by its ID.
     *
     * @param transactionId ID of the transaction
     * @return transaction response
     */
    TransactionResponse getTransactionById(Long transactionId);

    /**
     * Retrieves all ledger entries associated with a transaction.
     *
     * @param transactionId ID of the transaction
     * @return list of ledger entry responses
     */
    List<LedgerEntryResponse> getLedgerEntriesByTransaction(Long transactionId);
}