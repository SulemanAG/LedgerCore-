package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;

public interface TransactionService {

    /**
     * Transfers money from one account to another.
     *
     * @param request transfer details
     * @return completed transaction response
     */
    TransactionResponse transfer(TransferRequest request);
}