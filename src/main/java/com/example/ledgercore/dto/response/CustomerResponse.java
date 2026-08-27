package com.example.ledgercore.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents the customer information returned by the LedgerCore API.
 *
 * <p>This DTO acts as the public representation of a customer and prevents
 * the persistence entity from being exposed directly through the REST API.
 * Internal database state and financial relationships are intentionally kept
 * outside this response.</p>
 *
 * <p>The response contains the customer identifier assigned by LedgerCore
 * together with the customer's publicly relevant profile information.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Getter
@Setter
public class CustomerResponse {


    private Long customerId;


    private String customerName;


    private String customerAddress;


    private String customerPhoneNumber;

    private String customerEmail;

    public CustomerResponse() {
    }

    public CustomerResponse(
            Long customerId,
            String customerName,
            String customerAddress,
            String customerPhoneNumber,
            String customerEmail
    ) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.customerPhoneNumber = customerPhoneNumber;
        this.customerEmail = customerEmail;
    }

}