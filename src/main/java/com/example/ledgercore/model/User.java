package com.example.ledgercore.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an authentication identity within LedgerCore.
 *
 * <p>
 * User represents the credentials and security information used to
 * authenticate with the system, while {@link Customer} represents the
 * corresponding banking customer and their business information.
 * </p>
 *
 * <p>
 * The separation of User and Customer allows authentication concerns such as
 * username, password, role, and account enablement to remain separate from
 * financial-domain information.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "customer_id",
            nullable = false,
            unique = true
    )
    private Customer customer;

    public User() {
    }

    public User(
            Long userId,
            String username,
            String password,
            boolean enabled,
            Role role,
            Customer customer
    ) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.role = role;
        this.customer = customer;
    }
}