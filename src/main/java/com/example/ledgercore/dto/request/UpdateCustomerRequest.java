package com.example.ledgercore.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the Customer Information that can be modified through the
 * Ledger API.
 *
 * <P>
 *     Update data is kept separate from the customer creation data so that the
 *     rules governing an existing customer can evolve independently from the
 *     rules used when creating a new customer
 * </P>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Getter
@Setter
public class UpdateCustomerRequest {

    @NotBlank(message = "Customer name cannot be blank")
    @Size(max=100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Customer address cannot be blank")
    @Size(max=255, message = "Customer address cannot exceed 255 characters")
    private String customerAddress;

    @NotBlank(message = "Customer email cannot be blank")
    @Email(message = "Customer email must be valid")
    @Size(max=254, message = "Customer email cannot exceed 254 characters")
    private String customerEmail;

    @NotBlank(message = "Customer phone Number cannot be blank")
    @Pattern(regexp = "^[0-9]{10}$",
            message = "Customer phone number must contain exactly 10 digits")
    private String customerPhoneNumber;

    public UpdateCustomerRequest(){

    }

    public UpdateCustomerRequest(String customerName, String customerAddress, String customerEmail,
                                String customerPhoneNumber) {
        this.customerName=customerName;
        this.customerAddress=customerAddress;
        this.customerEmail=customerEmail;
        this.customerPhoneNumber=customerPhoneNumber;
    }


}
