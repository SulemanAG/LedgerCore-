package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.WithdrawalRequest;
import com.example.ledgercore.dto.response.TransactionResponse;

/**
 * Service interface for withdrawal operations.
 *
 * <p>
 * Defines the business operation for withdrawing money
 * from a customer account.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public interface WithdrawalService {

    /**
     * Withdraws money from a customer account.
     *
     * @param request withdrawal details
     * @return completed withdrawal transaction
     */
    TransactionResponse withdraw(WithdrawalRequest request);
}