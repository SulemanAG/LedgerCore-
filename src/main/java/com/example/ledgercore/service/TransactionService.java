package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.TransferRequest;
import com.example.ledgercore.dto.response.TransactionResponse;

import java.nio.file.AccessDeniedException;

public interface TransactionService {

    /**
     * Transfers money from one account to another.
     *
     * @param request transfer details
     * @return completed transaction
     */
    TransactionResponse transfer(TransferRequest request) throws AccessDeniedException;
}