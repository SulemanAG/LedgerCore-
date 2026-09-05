package com.example.ledgercore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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
