package com.example.ledgercore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the LedgerCore application.
 *
 * <p>
 * Enables Spring Boot auto-configuration and scheduled background
 * processing used by infrastructure components such as reconciliation.
 * </p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class LedgerCoreApplication {

	public static void main(String[] args) {

		SpringApplication.run(
				LedgerCoreApplication.class,
				args
		);
	}
}