package com.example.ledgercore.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the data required to create a new customer in LedgerCore.
 *
 * <p>This request contains only customer information that can be supplied by
 * the client. Persistence-managed fields such as the customer identifier and
 * financial accounts are intentionally excluded and are created or managed by
 * the server.</p>
 *
 * <p>Input validation is performed at the API boundary to prevent invalid
 * customer data from reaching the service and persistence layers.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Setter
@Getter
public class CreateCustomerRequest {


    @NotBlank(message = "Customer name cannot be blank")
    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Customer address cannot be blank")
    @Size(max = 255, message = "Customer address cannot exceed 255 characters")
    private String customerAddress;


    @NotBlank(message = "Customer phone number cannot be blank")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Customer phone number must contain exactly 10 digits"
    )
    private String customerPhoneNumber;


    @NotBlank(message = "Customer email cannot be blank")
    @Email(message = "Customer email must be valid")
    @Size(max = 254, message = "Customer email cannot exceed 254 characters")
    private String customerEmail;

    public CreateCustomerRequest() {
    }

    public CreateCustomerRequest(
            String customerName,
            String customerAddress,
            String customerPhoneNumber,
            String customerEmail
    ) {
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.customerPhoneNumber = customerPhoneNumber;
        this.customerEmail = customerEmail;
    }

}