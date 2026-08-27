package com.example.ledgercore.service;

import com.example.ledgercore.dto.request.CreateCustomerRequest;
import com.example.ledgercore.dto.request.UpdateCustomerRequest;
import com.example.ledgercore.dto.response.CustomerResponse;
import com.example.ledgercore.exception.CustomerNotFoundException;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.repository.CustomerRepository;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of {@link CustomerService}.
 *
 * <p>Provides the business logic required to create, retrieve, update, and
 * remove customer records while delegating persistence operations to
 * {@link CustomerRepository}.</p>
 *
 * <p>The implementation converts API request DTOs into {@link Customer}
 * entities before persistence and converts persisted entities into response
 * DTOs before returning data to the controller.</p>
 *
 * <p>Business rules that become necessary as LedgerCore evolves, such as
 * customer uniqueness, account ownership, lifecycle restrictions, and
 * validation, will be enforced at this layer rather than inside controllers.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public CustomerResponse createCustomer(CreateCustomerRequest request) {

        Customer customer = new Customer();

        customer.setCustomerName(request.getCustomerName());
        customer.setCustomerAddress(request.getCustomerAddress());
        customer.setCustomerPhoneNumber(request.getCustomerPhoneNumber());
        customer.setCustomerEmail(request.getCustomerEmail());

        Customer savedCustomer = customerRepository.save(customer);

        return mapToResponse(savedCustomer);
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public CustomerResponse getCustomerById(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        return mapToResponse(customer);
    }

    @Override
    public CustomerResponse updateCustomer(
            Long customerId,
            @NonNull UpdateCustomerRequest request
    ) {

        Customer existingCustomer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        existingCustomer.setCustomerName(request.getCustomerName());
        existingCustomer.setCustomerAddress(request.getCustomerAddress());
        existingCustomer.setCustomerPhoneNumber(request.getCustomerPhoneNumber());
        existingCustomer.setCustomerEmail(request.getCustomerEmail());

        Customer updatedCustomer =
                customerRepository.save(existingCustomer);

        return mapToResponse(updatedCustomer);
    }

    @Override
    public void deleteCustomer(Long customerId) {

        Customer existingCustomer = customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        customerRepository.delete(existingCustomer);
    }

    /**
     * Converts a persisted Customer entity into the response representation
     * exposed by the LedgerCore API.
     *
     * @param customer customer entity to convert
     * @return customer response DTO
     */
    @Contract("_ -> new")
    private @NonNull CustomerResponse mapToResponse(@NonNull Customer customer) {

        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getCustomerName(),
                customer.getCustomerAddress(),
                customer.getCustomerPhoneNumber(),
                customer.getCustomerEmail()
                );
    }
}