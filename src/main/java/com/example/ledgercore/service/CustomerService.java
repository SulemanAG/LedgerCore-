package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.CreateCustomerRequest;
import com.example.ledgercore.dto.request.UpdateCustomerRequest;
import com.example.ledgercore.dto.response.CustomerResponse;

import java.util.List;

/**
 * Defines the business operations available for managing LedgerCore customers.
 *
 * <p>The service layer separates customer-related business rules from the
 * REST controllers and persistence layer. It also acts as the boundary between
 * API DTOs and the internal customer domain model.</p>
 *
 * <p>Request DTOs are accepted from the API while response DTOs are returned
 * to the API. The underlying {@code Customer} entity is intentionally kept
 * inside the service and persistence layers.</p>
 *
 * <p>Customer operations are intentionally kept separate from authentication
 * and authorization concerns, which are handled by the security subsystem.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
public interface CustomerService {

    CustomerResponse createCustomer(CreateCustomerRequest request);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse getCustomerById(Long customerId);

    CustomerResponse updateCustomer(
            Long customerId,
            UpdateCustomerRequest request
    );

    void deleteCustomer(Long customerId);
}