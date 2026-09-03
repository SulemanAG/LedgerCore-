package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;

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
}