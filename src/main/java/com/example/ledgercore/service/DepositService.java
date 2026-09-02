package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.DepositRequest;
import com.example.ledgercore.dto.response.TransactionResponse;

/**
 * Service interface responsible for deposit operations.
 *
 * <p>
 * Defines the business operation for depositing money into
 * a customer account. The implementation is responsible for
 * performing the corresponding double-entry accounting operation.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public interface DepositService {

    /**
     * Deposits money into a customer account.
     *
     * <p>
     * A successful deposit will create a transaction containing:
     * </p>
     *
     * <ul>
     *     <li>One DEBIT ledger entry for the SYSTEM account</li>
     *     <li>One CREDIT ledger entry for the customer account</li>
     *     <li>An increase in the customer account balance</li>
     * </ul>
     *
     * @param request deposit details
     * @return response containing the created transaction
     */
    TransactionResponse deposit(DepositRequest request);
}