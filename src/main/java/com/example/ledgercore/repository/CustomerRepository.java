package com.example.ledgercore.repository;

import com.example.ledgercore.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository responsible for persistence operations on LedgerCore customers.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerPhoneNumber(String customerPhoneNumber);
}