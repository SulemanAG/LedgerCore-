package com.example.ledgercore.config;

import com.example.ledgercore.model.Account;
import com.example.ledgercore.model.AccountStatus;
import com.example.ledgercore.model.AccountType;
import com.example.ledgercore.model.Currency;
import com.example.ledgercore.model.Customer;
import com.example.ledgercore.repository.AccountRepository;
import com.example.ledgercore.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Initializes LedgerCore's internal SYSTEM account.
 *
 * <p>
 * The SYSTEM account acts as the accounting counterpart for
 * external financial movements such as deposits.
 * </p>
 *
 * <p>
 * The initializer is idempotent: it creates the system account
 * only when it does not already exist.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Configuration
public class SystemAccountInitializer {

    private static final String SYSTEM_ACCOUNT_NUMBER =
            "LC-SYSTEM-INR";

    @Bean
    CommandLineRunner initializeSystemAccount(
            CustomerRepository customerRepository,
            AccountRepository accountRepository
    ) {

        return args -> {

          // We use the unique account number as the identifier.

            boolean systemAccountExists =
                    accountRepository
                            .findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                            .isPresent();

            if (systemAccountExists) {
                return;
            }


            //Find or create the special SYSTEM customer.

            Customer systemCustomer =
                    customerRepository
                            .findByCustomerPhoneNumber("SYSTEM")
                            .orElseGet(() -> {

                                Customer customer = new Customer();

                                customer.setCustomerName("LedgerCore System");
                                customer.setCustomerEmail("system@ledgercore.internal");
                                customer.setCustomerPhoneNumber("SYSTEM");
                                customer.setCustomerAddress("INTERNAL");
                                return customerRepository.save(customer);
                            });

           // Create the SYSTEM account.

            Account systemAccount = new Account();

            systemAccount.setAccountNumber(
                    SYSTEM_ACCOUNT_NUMBER
            );

            systemAccount.setBalance(BigDecimal.ZERO);

            systemAccount.setCurrency(Currency.INR);

            systemAccount.setStatus(AccountStatus.ACTIVE);

            systemAccount.setAccountType(AccountType.SYSTEM);

            systemAccount.setCustomer(systemCustomer);

            accountRepository.save(systemAccount);
        };
    }
}