package com.example.ledgercore.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a customer registered with the LedgerCore system.
 *
 * <p>A customer may own multiple financial accounts. The customer-account
 * relationship is intentionally independent of the account lifecycle so that
 * financial accounts and their associated history are not automatically
 * deleted when customer data is modified.
 *
 * <p>Accounts are loaded lazily because a customer may eventually have
 * multiple accounts containing a potentially large amount of associated
 * financial data. Authentication and authorization information is kept
 * separate from this entity and belongs to the security subsystem.
 *
 * @author Suleman Agasimani
 * @since 1.0
 */

@Getter
@Setter
@Entity
@Table(name="customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerAddress;

    @Column(nullable = false, length = 20)
    private String customerPhoneNumber;

    @Column(nullable = false,unique = true, length = 254)
    private String customerEmail;

    @OneToMany(
            mappedBy = "customer",
            fetch = FetchType.LAZY
    )
    private List<Account> accounts = new ArrayList<>();

    public Customer(){
        //No-arg
    }

    public Customer(Long customerId, String customerName, String customerAddress, String customerPhoneNumber, String customerEmail, List<Account> accounts) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.customerPhoneNumber = customerPhoneNumber;
        this.customerEmail = customerEmail;
        this.accounts = accounts;
    }
}
