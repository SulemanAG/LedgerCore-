# 🏦 LedgerCore

> **A resilient, production-grade financial backend, double-entry ledger engine, and event-driven balance projection architecture built with Java 17, Spring Boot 3, PostgreSQL, Kafka, and Redis.**

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Spring Security](https://img.shields.io/badge/Security-Spring%20Security%206-6DB33F?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Tests](https://img.shields.io/badge/Tests-50%2F50%20Passing-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

<p align="center">
  <img src="./ledgercore-architecture.svg" alt="LedgerCore System Architecture and Flow" width="100%" />
</p>

---

## 📌 Project Overview

**LedgerCore** is a high-throughput, enterprise-grade banking backend and double-entry financial ledger engine developed with **Java 17** and **Spring Boot 3**. Designed specifically for mission-critical financial software engineering, LedgerCore rejects simplified "CRUD-style" account balance updates in favor of strict double-entry ledger accounting, transactional outbox delivery, multi-partition event streaming, atomic read-side balance projections, and robust failure-recovery mechanisms.

The system enforces a clear separation of concerns across its persistence, messaging, and caching layers:
* **PostgreSQL (Authoritative Source of Truth)**: Executes all financial transactions within ACID boundaries. Manages double-entry ledger journal entries (`DEBIT`/`CREDIT`), account entities guarded by JPA `@Version` optimistic locking, durable transactional outbox records (`outbox_events`), and consumer idempotency states (`processed_events`).
* **Redis (Eventually Consistent Read Projection)**: Serves low-latency, read-side account balance queries (`ledgercore:account:{id}:balance`). Redis balance updates are applied asynchronously from event streams using an **atomic Lua script** (`setBalanceIfVersionGreater`) that checks account `@Version` metadata to reject duplicate or out-of-order event deliveries.
* **Apache Kafka (Event Propagation & Streaming)**: Handles asynchronous distribution of financial events (`ledgercore-transactions`). Messages are published using aggregate account IDs as Kafka keys to guarantee strict per-account event ordering across topic partitions.
* **Transactional Outbox & Relay**: Financial operations persist transaction outbox records atomically alongside business logic. The `OutboxRelayService` polls pending records using PostgreSQL `SKIP LOCKED` row locking to eliminate lock contention during concurrent worker scaling.
* **Bounded Consumer Retry & DLT Handling**: Consumer processing errors trigger a Spring Kafka `DefaultErrorHandler` policy (3 total attempts with 1,000ms fixed backoff). Events failing all retries are published to a Dead-Letter Topic (`ledgercore-transactions.DLT`) with original keys, payloads, and DLT exception headers intact.
* **Reconciliation & Projection Backfill**: Implemented background operational services ensure long-term state consistency. `ReconciliationService` detects missing or orphaned projections; `RedisOrphanCleanupService` safely removes stale Redis keys lacking PostgreSQL entity backing; and `AccountProjectionBackfillService` rebuilds missing Redis balance projections directly from authoritative PostgreSQL records.

> ℹ️ **Automated Verification**: The repository contains **50 automated integration, concurrency, Kafka ordering, idempotency, failure-path, retry, DLT, and reconciliation tests**, verified at **50/50 passing with 0 failures, 0 errors, and 0 skipped**.
> 
> 🎯 **Performance Target Note**: The architecture is designed to target **1,000+ Transactions Per Second (TPS)** via asynchronous outbox relay, multi-partition Kafka topic distribution, and Redis read projections. This throughput is a **design performance target** and is scheduled for formal benchmarking during the upcoming JMeter performance validation phase.

---

## 📑 Table of Contents

- [📌 Project Overview](#-project-overview)
- [🛠️ Key Engineering Capabilities](#️-key-engineering-capabilities)
- [🧰 Technology Stack](#-technology-stack)
- [🏛️ System Architecture & Data Flow](#️-system-architecture--data-flow)
- [🧩 Implemented Financial Domain & Entity Model](#-implemented-financial-domain--entity-model)
- [💸 Financial Operation Lifecycles](#-financial-operation-lifecycles)
- [⚖️ Double-Entry Accounting Model](#-double-entry-accounting-model)
- [🛡️ Security, API Design, Authorization & Error Handling](#️-security-api-design-authorization--error-handling)
- [🔄 Event-Driven Architecture](#-event-driven-architecture)
- [📦 Transactional Outbox Pattern & Relay Engine](#-transactional-outbox-pattern--relay-engine)
- [📨 Apache Kafka Streaming & Consumer Reliability](#-apache-kafka-streaming--consumer-reliability)
- [⚡ Redis Read-Side Balance Projections & Reconciliation](#-redis-read-side-balance-projections--reconciliation)
- [🚨 System Stage Boundaries, Consistency Properties & Failure Model](#-system-stage-boundaries-consistency-properties--failure-model)
- [🧪 Testing & Reliability](#-testing--reliability)
- [📡 REST API Reference](#-rest-api-reference)
- [📂 Project Directory Structure](#-project-directory-structure)
- [🚀 Local Setup & Configuration](#-local-setup--configuration)
- [🛡️ Core Engineering Guarantees](#️-core-engineering-guarantees)
- [🎯 What This Project Demonstrates](#-what-this-project-demonstrates)
- [📊 Performance Target & Architectural Scope](#-performance-target--architectural-scope)
- [🔮 Future Hardening & Roadmap](#-future-hardening--roadmap)
- [👨‍💻 Author](#-author)

---

## 🛠️ Key Engineering Capabilities

| Engineering Capability | Architectural Implementation & Purpose |
| :--- | :--- |
| **Double-Entry Ledger Accounting** | Immutably records every transaction as balanced `DEBIT` and `CREDIT` entries. Prevents arbitrary balance mutations and ensures total debits equal total credits. |
| **Optimistic Concurrency Control** | Protects PostgreSQL `Account` entities with JPA `@Version` locking. Rejects concurrent race conditions cleanly via `OptimisticLockingFailureException`. |
| **Transactional Outbox Pattern** | Writes financial events to `outbox_events` within the same DB transaction boundary. Eliminates dual-write anomalies between PostgreSQL and Kafka. |
| **High-Throughput Outbox Relay** | `OutboxRelayService` claims pending outbox records using PostgreSQL `SKIP LOCKED`. Prevents worker thread blocking and duplicate event fetching. |
| **Ordered Multi-Partition Kafka Streaming** | Uses aggregate account ID as Kafka key (`ledgercore-transactions`). Routes related account events to identical partitions for strict in-order consumer processing. |
| **Dual Per-Account Transfer Events** | Transfers emit `TRANSFER_SOURCE_DEBITED` and `TRANSFER_DESTINATION_CREDITED` events with distinct aggregate keys, ensuring independent Kafka partition ordering for both accounts. |
| **Durable Consumer Idempotency** | `EventProcessingService` tracks processed events in PostgreSQL `processed_events` using unique `event_id` constraints. Prevents duplicate event execution. |
| **Atomic Lua Version-Guarded Redis Projection** | Executes `setBalanceIfVersionGreater` script in Redis. Ignores out-of-order or duplicate event payloads by comparing incoming `@Version` against Redis version metadata. |
| **Consumer Retry & DLT Recovery** | `DefaultErrorHandler` provides 3 bounded retry attempts (1s backoff). Unrecoverable failures are published to `ledgercore-transactions.DLT` via `DeadLetterPublishingRecoverer`. |
| **Reconciliation & Projection Backfill** | Operational services (`ReconciliationService`, `RedisOrphanCleanupService`, `AccountProjectionBackfillService`) audit state, purge orphaned Redis keys, and rebuild missing projections. |
| **Ownership-Based Authorization** | Custom Spring Security authorization ensures authenticated users (`User`) can only access or mutate accounts owned by their verified identity (`Customer`). |

---

## 🧰 Technology Stack

### Core Framework & Runtime
* **Java 17**: Modern Long-Term Support (LTS) Java runtime utilizing sealed classes, records, and pattern matching.
* **Spring Boot 3.4.x**: Main application framework providing dependency injection, autoconfiguration, and embedded Tomcat runtime.

### Persistence & Data Storage
* **PostgreSQL**: Primary transactional database and authoritative financial source of truth.
* **Spring Data JPA & Hibernate**: Object-Relational Mapping (ORM) with `@Version` optimistic locking, `@Transactional` boundaries, and custom repository queries using `SKIP LOCKED`.
* **Redis & Spring Data Redis (Lettuce)**: Read-side balance projection caching with `StringRedisTemplate` and atomic Lua script execution.

### Messaging & Event Streaming
* **Apache Kafka & Spring Kafka**: Asynchronous message broker handling event distribution, multi-partition topic management (`ledgercore-transactions`), custom consumer groups (`ledgercore-consumer`), and DLT publishing (`ledgercore-transactions.DLT`).

### Security & Validation
* **Spring Security 6**: Authentication and authorization framework implementing BCrypt password hashing (`BCryptPasswordEncoder`) and ownership-based method security.
* **Jakarta Bean Validation**: Declarative DTO input validation (`@NotNull`, `@Positive`, `@NotBlank`, `@Size`).

### Testing & Quality Assurance
* **JUnit 5 & Spring Boot Test**: Integration test suite verifying concurrency, outbox delivery, Kafka ordering, idempotency, failure-path retries, DLT forwarding, and reconciliation routines.

---

## 🏛️ System Architecture & Data Flow

### Complete End-to-End Execution Flow

Every financial operation in LedgerCore travels through a strictly sequenced, decoupled architecture:

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 Client / API Consumer
    participant Sec as 🛡️ Spring Security
    participant AuthGuard as 👮 Ownership Guard
    participant Controller as 🎮 REST Controller
    participant Service as ⚙️ Service Layer
    participant DB as 🐘 PostgreSQL (ACID)
    participant Relay as 🔄 Outbox Relay (SKIP LOCKED)
    participant Kafka as 📨 Apache Kafka (Multi-Partition)
    participant Consumer as 📥 Kafka Consumer Service
    participant EventProc as ⚙️ Event Processing Service
    participant Redis as ⚡ Redis Read Projection

    Client->>Sec: 1. POST /accounts/transfer (Credentials + Request DTO)
    Sec->>Sec: 2. Authenticate BCrypt credentials
    Sec->>AuthGuard: 3. Ownership Authorization check
    AuthGuard->>Controller: 4. Pass validated request
    Controller->>Service: 5. Invoke transfer business logic
    
    critical Atomic PostgreSQL Transaction (@Transactional)
        Service->>DB: 6. Lock Idempotency Key (Advisory Lock)
        Service->>DB: 7. Check & update Source Account balance
        Service->>DB: 8. Check & update Destination Account balance
        Service->>DB: 9. Save DEBIT & CREDIT Ledger Entries
        Service->>DB: 10. Save Transaction Record (COMPLETED)
        Service->>DB: 11. Flush Account entities to obtain @Version
        Service->>DB: 12. Save Dual Outbox Events (PENDING)
    end
    DB-->>Service: 13. Transaction Committed Atomically
    Service-->>Client: 14. Return TransactionResponse DTO (200 OK)

    note over Relay,Kafka: Asynchronous Post-Commit Event Delivery Pipeline

    loop Fixed Delay (5,000ms)
        Relay->>DB: 15. Claim PENDING outbox events (SKIP LOCKED)
        Relay->>Kafka: 16. Publish KafkaEvent with aggregate account ID key
        Kafka-->>Relay: 17. Kafka ACK received
        Relay->>DB: 18. Mark outbox event PUBLISHED
    end

    Kafka->>Consumer: 19. Consumer poll (ledgercore-consumer)
    Consumer->>EventProc: 20. Process KafkaEvent
    
    critical Durable Consumer Idempotency Check
        EventProc->>DB: 21. Check processed_events table by eventId
        alt Duplicate Event
            EventProc-->>Consumer: 22. Ignore duplicate & skip processing
        else New Event
            EventProc->>Redis: 23. Execute Atomic Lua Version Guard (setBalanceIfVersionGreater)
            Redis-->>EventProc: 24. Projection updated (if version > current)
            EventProc->>DB: 25. Insert ProcessedEvent record (REQUIRES_NEW transaction)
        end
    end
```

---

### Component Responsibilities & Separation of Concerns

LedgerCore strictly decouples its system components into discrete architectural layers:

1. **Spring Security & Ownership Authorization**:
   - Authenticates credentials (`User` entity with BCrypt hash).
   - Validates that the logged-in user owns the target account via `AccountAuthorizationService` before business logic execution.

2. **REST Controllers & DTO Validation**:
   - Exposes clean REST endpoints (`DepositController`, `WithdrawalController`, `TransactionController`, `AccountController`).
   - Enforces declarative request constraints via Jakarta Bean Validation (`@NotNull`, `@Positive`). Sanitizes output DTOs to prevent entity exposure.

3. **Service Layer & Double-Entry Ledger Engine**:
   - Executes core financial rules inside `@Transactional` boundaries.
   - Enforces double-entry ledger invariants (`Sum(DEBIT) == Sum(CREDIT)`).
   - Flushes entity updates to extract post-mutation JPA `@Version` sequence numbers (`saveAndFlush()`).

4. **PostgreSQL Database (Authoritative Source of Truth)**:
   - Primary relational datastore executing transactions with ACID guarantees.
   - Persists immutable ledger entries, transactions, account states, transactional outbox records (`outbox_events`), and idempotency records (`processed_events`).

5. **Transactional Outbox & Relay (`OutboxRelayService`)**:
   - Decouples database transactions from Kafka publishing.
   - Periodically polls pending outbox events using PostgreSQL `SKIP LOCKED` row locking to allow horizontal worker scaling without lock contention.

6. **Apache Kafka Message Broker**:
   - Provides durable, multi-partition event streaming (`ledgercore-transactions`).
   - Uses aggregate account IDs as Kafka message keys to route related events to identical partitions for strictly ordered delivery.

7. **Kafka Consumer & Event Processing (`KafkaConsumerService` & `EventProcessingService`)**:
   - Consumes Kafka events asynchronously.
   - Enforces bounded retry (3 attempts) and DLT recovery via Spring Kafka `DefaultErrorHandler` and `DeadLetterPublishingRecoverer`.
   - Manages durable consumer idempotency (`ProcessedEventService`) and delegates Redis projection updates.

8. **Redis Read Projection & Lua Version Guard (`AccountBalanceRedisService`)**:
   - In-memory datastore serving fast, low-latency account balance read queries (`/accounts/{id}`).
   - Executes an atomic Lua script comparing incoming `@Version` metadata against current Redis version state to prevent stale or duplicate out-of-order balance regression.

---

## 🧩 Implemented Financial Domain & Entity Model

LedgerCore treats **PostgreSQL as the authoritative financial source of truth**. All account balances, transaction records, and double-entry ledger journals are persisted within PostgreSQL relational transactions under strict ACID constraints.

The domain model decouples **Authentication Credentials** (`User`), **Banking Customers** (`Customer`), **Financial Accounts** (`Account`), **Business Operations** (`Transaction`), and **Accounting Entries** (`LedgerEntry`).

```
                    ┌──────────────────┐
                    │     Customer     │
                    └────────┬─────────┘
                             │ owns (1:N)
                             ▼
                    ┌──────────────────┐
                    │     Account      │
                    └────────┬─────────┘
                             │ participates (1:N)
                             ▼
                    ┌──────────────────┐
                    │   LedgerEntry    │ ◄─── balanced by (1:2+)
                    └────────┬─────────┘
                             │ belongs to (N:1)
                             ▼
                    ┌──────────────────┐
                    │   Transaction    │
                    └────────┬─────────┘
                             │ produces (1:1 or 1:2)
                             ▼
                    ┌──────────────────┐
                    │   OutboxEvent    │
                    └──────────────────┘
```

### Entity Relationship & Core Definitions

* **`Account`**: Represents a financial account with a monetary balance and currency. Maintained in PostgreSQL with a `@Version` column for optimistic locking. Accounts are owned by a `Customer` and transition through lifecycle states (`ACTIVE`, `FROZEN`, `CLOSED`).
* **`Transaction`**: Represents the business operation of moving money (e.g., Deposit, Withdrawal, Transfer). Stores the transaction type, status, overall amount, currency, timestamp, human-readable reference, and unique `idempotencyKey`.
* **`LedgerEntry`**: Represents the granular, immutable accounting movements produced by a transaction. Each transaction generates balanced `DEBIT` and `CREDIT` ledger entries referencing specific accounts.
* **`OutboxEvent`**: Represents a transactional outbox event created inside the same PostgreSQL transaction as the financial operation. Stores event type, aggregate ID, payload JSON, status (`PENDING`, `PROCESSING`, `SENT`, `FAILED`), and retry metadata.
* **`ProcessedEvent`**: Represents a consumed Kafka event recorded in PostgreSQL to ensure strict consumer-side idempotency.

### Exact Entity Schema & Field Specifications

| Entity | Primary Key / Identifiers | Important Fields | Relationships & Versioning |
| :--- | :--- | :--- | :--- |
| **`Account`** | `accountId` (`Long`, PK) | `accountNumber` (`String`, UK)<br>`balance` (`BigDecimal`, 19,4)<br>`currency` (`INR`, `USD`, `EUR`, `GBP`)<br>`status` (`ACTIVE`, `FROZEN`, `CLOSED`)<br>`accountType` (`CHECKING`, `SAVINGS`, `SYSTEM`) | `@Version private Long version;`<br>`@ManyToOne private Customer customer;` |
| **`Transaction`** | `transactionId` (`Long`, PK) | `amount` (`BigDecimal`, 19,4)<br>`currency` (`Currency`)<br>`status` (`TransactionStatus.COMPLETED`)<br>`reference` (`String`, 100)<br>`idempotencyKey` (`String`, UK)<br>`createdAt` (`LocalDateTime`) | `@OneToMany private List<LedgerEntry> ledgerEntries;` |
| **`LedgerEntry`** | `ledgerEntryId` (`Long`, PK) | `amount` (`BigDecimal`, 19,4)<br>`entryType` (`LedgerEntryType.DEBIT`, `LedgerEntryType.CREDIT`) | `@ManyToOne private Transaction transaction;`<br>`@ManyToOne private Account account;` |
| **`OutboxEvent`** | `eventId` (`Long`, PK) | `eventType` (`String`, 50)<br>`aggregateId` (`Long`)<br>`payload` (`TEXT` JSON)<br>`status` (`OutboxEventStatus.PENDING`)<br>`createdAt`, `nextAttempt`, `retryCount`, `processingStartedAt` | Outbox table persisted in PostgreSQL; published asynchronously to Kafka by `OutboxRelayService`. |
| **`ProcessedEvent`** | `id` (`Long`, PK) | `eventId` (`Long`, UK)<br>`eventType` (`String`)<br>`processedAt` (`LocalDateTime`) | Consumed event tracking table in PostgreSQL to ensure strict consumer idempotency. |

---

## ⚖️ Double-Entry Accounting Model

LedgerCore strictly adheres to **double-entry accounting principles**. Money is never created, destroyed, or mutated in isolation; every financial movement is represented by matching `DEBIT` and `CREDIT` entries such that:

$$\sum \text{Debits} = \sum \text{Credits}$$

### Accounting Entries & Entry Types

The system uses explicit enum types (`LedgerEntryType.DEBIT` and `LedgerEntryType.CREDIT`):

* **`DEBIT`**: Represents an accounting entry that decreases an account balance (for asset accounts) or records outgoing value.
* **`CREDIT`**: Represents an accounting entry that increases an account balance (for asset accounts) or records incoming value.

### Concrete Accounting Movement Example (₹1,000 Transfer)

```text
Transfer Operation: ₹1,000 from Source Account (101) to Destination Account (202)

Debit Entry:
  Account:      Source Account (101)
  Entry Type:   DEBIT
  Amount:       ₹1,000.00

Credit Entry:
  Account:      Destination Account (202)
  Entry Type:   CREDIT
  Amount:       ₹1,000.00

Accounting Balance Check: ₹1,000.00 (DEBIT) == ₹1,000.00 (CREDIT)  [BALANCED ✅]
```

### Mutable Balance vs. Immutable Ledger Journal

| Concept | Implementation in LedgerCore | Engineering Purpose |
| :--- | :--- | :--- |
| **Account Balance (`balance`)** | `Account.balance` column updated directly in PostgreSQL. | Provides efficient, $O(1)$ balance reads and validation checks without scanning historical records. |
| **Ledger Journal (`LedgerEntry`)** | Immutable row inserted into `ledger_entries` for every movement. | Provides an auditable, append-only historical log of how account balances reached their current state. |

> ⚠️ **Regulatory Clarification**: LedgerCore implements formal double-entry accounting invariants (`DEBIT`/`CREDIT` parity, transactional atomicity, and append-only ledger entries). However, it does not claim full regulatory banking compliance, external audit certification, or production banking license compliance.

---

## 💸 Financial Operation Lifecycles

### 1. Financial Flow Diagram

```mermaid
flowchart TD
    A[Client HTTP Request] --> B[DTO Validation @Valid]
    B --> C[Spring Security Ownership Authorization]
    C --> D[TransactionService / Service Layer]

    subgraph PG[PostgreSQL @Transactional Boundary]
        D --> E[Lock Idempotency Key DB Advisory Lock]
        E --> F[Check Account State & Balance]
        F --> G[Mutate Account Balances]
        G --> H[Create Transaction Record COMPLETED]
        H --> I[Create Balanced Ledger Entries DEBIT / CREDIT]
        I --> J[Flush Entities to Obtain @Version]
        J --> K[Create Outbox Event PENDING]
    end

    K --> L[COMMIT Database Transaction]

    subgraph Async[Asynchronous Event Delivery & Read Projection]
        L --> M[OutboxRelayService SKIP LOCKED]
        M --> N[Publish to Apache Kafka ledgercore-transactions]
        N --> O[Kafka Consumer Service]
        O --> P[EventProcessingService & ProcessedEvent Check]
        P --> Q[Execute Atomic Lua Script setBalanceIfVersionGreater]
        Q --> R[(Redis Read Projection)]
    end
```

---

### 2. Deposit Lifecycle (`POST /transactions/deposit`)

Deposits introduce external funds into a customer account. In LedgerCore, external capital enters via an internal system balancing account (`LC-SYSTEM-INR`), maintaining double-entry parity.

```text
Client
  ↓
POST /transactions/deposit
  ↓
DTO Validation (@Valid DepositRequest)
  ↓
DepositServiceImpl
  ↓
Account Ownership & Status Verification (ACTIVE)
  ↓
PostgreSQL @Transactional Boundary
  ├── Lock Idempotency Key (transactionRepository.lockIdempotencyKey)
  ├── Fetch Customer Account & System Ledger Account (LC-SYSTEM-INR)
  ├── Increase Customer Account Balance (+Amount)
  ├── Create Transaction Record (type = DEPOSIT, status = COMPLETED)
  ├── Create Ledger Entries:
  │     ├── System Account  ──► DEBIT  (Amount)
  │     └── Customer Account ──► CREDIT (Amount)
  ├── Save and Flush Account entities to capture post-mutation @Version
  └── Create OutboxEvent (eventType = DEPOSIT_COMPLETED, payload includes account version)
  ↓
COMMIT Database Transaction
  ↓
OutboxRelayService (SKIP LOCKED polling)
  ↓
Apache Kafka (topic: ledgercore-transactions, key: accountId)
  ↓
Redis Read-Side Balance Projection (via atomic Lua script)
```

#### Financial Invariant
A successful deposit must **never result in a balance update without corresponding transaction and ledger records**. The Spring `@Transactional` boundary guarantees that either the account balance increase, transaction record, ledger entries, and outbox event all commit together, or none of them persist.

---

### 3. Withdrawal Lifecycle (`POST /transactions/withdraw`)

Withdrawals extract funds from a customer account into the internal system account (`LC-SYSTEM-INR`).

```text
Client
  ↓
POST /transactions/withdraw
  ↓
DTO Validation (@Valid WithdrawalRequest)
  ↓
WithdrawalServiceImpl
  ↓
Account Ownership Check (Caller must own account)
  ↓
PostgreSQL @Transactional Boundary
  ├── Lock Idempotency Key
  ├── Fetch Customer Account & Verify Status == ACTIVE
  ├── Verify Balance Sufficiency: account.getBalance().compareTo(amount) >= 0
  │     └── IF Balance < Amount ──► Throw InsufficientFundsException (Rollback)
  ├── Decrease Customer Account Balance (-Amount)
  ├── Create Transaction Record (type = WITHDRAWAL, status = COMPLETED)
  ├── Create Ledger Entries:
  │     ├── Customer Account ──► DEBIT  (Amount)
  │     └── System Account   ──► CREDIT (Amount)
  ├── Flush Account entities to capture post-mutation @Version
  └── Create OutboxEvent (eventType = WITHDRAWAL_COMPLETED, payload includes account version)
  ↓
COMMIT Database Transaction
```

#### Rollback & Failure Behavior
If a withdrawal fails (e.g., `InsufficientFundsException`, invalid account status, or database deadlock), the `@Transactional` boundary triggers a **complete database rollback**. The customer balance remains unchanged, no transaction or ledger entries are recorded, and no outbox event is created.

---

### 4. Transfer Lifecycle (`POST /transactions/transfer`)

Transfers execute money movements between two customer accounts.

```text
Source Account (101)
      │
      │ DEBIT (-₹1,000)
      ▼
Transfer Transaction
      │
      │ CREDIT (+₹1,000)
      ▼
Destination Account (202)
```

#### Core Accounting & Outbox Representation
1. **Source Account**: Debited by `amount`, balance decreases, `@Version` increments.
2. **Destination Account**: Credited by `amount`, balance increases, `@Version` increments.
3. **Ledger Entries**: One `DEBIT` entry for `sourceAccount`, one `CREDIT` entry for `destinationAccount`.
4. **Dual Per-Account Outbox Events**:
   - `TRANSFER_SOURCE_DEBITED`: `aggregateId` = `sourceAccountId`, Kafka key = `sourceAccountId.toString()`, payload includes `sourceAccountVersion`.
   - `TRANSFER_DESTINATION_CREDITED`: `aggregateId` = `destinationAccountId`, Kafka key = `destinationAccountId.toString()`, payload includes `destinationAccountVersion`.

#### Implemented Transfer Guarantees
* **Single Database Transaction**: Source debit, destination credit, transaction record, ledger entries, and dual outbox events occur inside **one atomic PostgreSQL transaction**.
* **Atomic Rollback**: Any failure (insufficient funds, frozen account, optimistic lock conflict) rolls back the entire operation.
* **Accounting Consistency**: `DEBIT` amount equals `CREDIT` amount exactly.
* **Ownership Authorization**: Caller must be the authorized owner of the source account.
* **Optimistic Locking**: JPA `@Version` on both accounts prevents lost updates from concurrent transfers.
* **Outbox Version Payload**: Post-operation account version sequence numbers are captured post-flush and included in outbox event payloads for atomic Lua Redis version checking.

---

## 🛡️ Security, API Design, Authorization & Error Handling

LedgerCore enforces a strict separation between **Authentication** ("Who is making the request?") and **Authorization** ("Is this authenticated user allowed to access or modify this specific resource?").

```text
Client
  ↓
HTTP Request (Header: Authorization: Basic <base64 credentials>)
  ↓
Spring Security Filter Chain (SecurityConfig)
  ├── 1. CSRF Disabled (Stateless REST API)
  ├── 2. Match URL Path (Public permitAll vs Protected authenticated)
  └── 3. DaoAuthenticationProvider (Loads User via CustomUserDetailsService + BCrypt)
  ↓
REST Controller Layer (@Valid Request DTO)
  ├── 4. Jakarta Bean Validation (@NotNull, @NotBlank, @DecimalMin, @Size, @Pattern, @Email)
  └── 5. Dispatch to Service Layer
  ↓
Service Layer Ownership Guard
  ├── 6. AccountAuthorizationService.isOwner(account)
  │     └── IF user.getCustomerId() != account.getCustomerId() ──► Throw AccessDeniedException (403)
  └── 7. Domain & Financial Rules Validation
  ↓
PostgreSQL Transaction (@Transactional Boundary)
  └── 8. Atomic Financial Mutation & Outbox Persistence
```

---

### 1. Authentication Engine & Credentials (`SecurityConfig`)

LedgerCore implements **HTTP Basic Authentication** configured through Spring Security 6 (`SecurityConfig`).

#### Authentication Mechanics & Credential Flow
* **Transport Credential**: Clients supply credentials via the `Authorization: Basic <base64(username:password)>` HTTP header.
* **Authentication Provider**: Spring Security's `DaoAuthenticationProvider` delegates username lookup to `CustomUserDetailsService`.
* **Database User Retrieval**: `CustomUserDetailsService` queries the `users` table via `UserRepository.findByUsername(username)`.
* **Password Encoding**: Passwords are verified against stored one-way BCrypt hashes using `BCryptPasswordEncoder`.
* **Principal & Authorities**: Authenticated users are represented as Spring Security `UserDetails` objects with assigned authorities (`ROLE_CUSTOMER` or `ROLE_ADMIN`).

#### Endpoint Security Boundaries

| Security Boundary | HTTP Method | Endpoint Pattern | Purpose |
| :--- | :--- | :--- | :--- |
| **PUBLIC (`permitAll`)** | `POST` | `/customer` | Allows new banking customers to self-register. |
| **PUBLIC (`permitAll`)** | `POST` | `/customer/*/users` | Allows creating login credentials for a customer. |
| **PUBLIC (`permitAll`)** | `ANY` | `/error` | Spring Boot default error routing endpoint. |
| **AUTHENTICATED (`authenticated`)** | `ALL` | `/accounts/**`, `/transactions/**`, `/customer/**` | All account queries, deposits, withdrawals, transfers, and customer profile updates. |

```http
POST /transactions/transfer HTTP/1.1
Host: localhost:8081
Authorization: Basic dXNlckE6cGFzc3dvcmQxMjM=
Content-Type: application/json

{
  "sourceAccountId": 101,
  "destinationAccountId": 202,
  "amount": 1000.00,
  "currency": "INR",
  "reference": "Monthly savings",
  "idempotencyKey": "TX-AUTH-9901"
}
```

---

### 2. Customer → User → Account Ownership Hierarchy

The core database design enforces a strict hierarchy connecting real-world banking customers to login credentials and financial accounts:

```text
       ┌────────────────────────────────────────┐
       │                Customer                │
       │  (Real-World Entity, KYC, Name, Email) │
       └───────────────────┬────────────────────┘
                           │
             ┌─────────────┴─────────────┐
             │ 1:1                       │ 1:N
             ▼                           ▼
┌─────────────────────────┐  ┌─────────────────────────┐
│          User           │  │         Account         │
│  (Auth Credentials,     │  │  (Financial Container,  │
│   BCrypt Hash, Role)    │  │   Balance, @Version)    │
└─────────────────────────┘  └─────────────────────────┘
```

* **`Customer`**: Primary domain entity representing a banking client (`customerId`, `customerName`, `customerEmail`, `customerPhone`).
* **`User`**: Security credentials entity (`userId`, `username`, `password` hash, `role`, `enabled`) linked to a `Customer` via `customer_id` foreign key.
* **`Account`**: Financial balance container (`accountId`, `accountNumber`, `balance`, `currency`, `@Version`) linked to a `Customer` via `customer_id` foreign key.

---

### 3. Account-Level Ownership Authorization Guard

Authenticating a request proves **who the user is**, but does **not** grant permission to access arbitrary accounts. LedgerCore prevents unauthorized cross-account access by enforcing a dedicated service-level ownership guard (`AccountAuthorizationService`).

```text
Request: POST /transactions/transfer (sourceAccountId = 101)
  │
  ├─ Authenticated User: "userA" (linked to Customer ID: 1)
  ├─ Target Account: Account 101 (owned by Customer ID: 2)
  │
  ▼
AccountAuthorizationService.isOwner(sourceAccount)
  │
  ├── user.getCustomer().getCustomerId() == 1
  ├── account.getCustomer().getCustomerId() == 2
  │
  └─► Match? NO! ──► Throw org.springframework.security.access.AccessDeniedException
                          │
                          ▼
             HTTP 403 Forbidden (Transaction Rejected BEFORE any DB mutation!)
```

#### Protected Financial Boundaries
* `GET /accounts/{accountId}`: Caller must be the verified owner of `{accountId}`.
* `GET /accounts/customer/{customerId}`: Caller must be the customer matching `{customerId}` (`isCustomerOwner`).
* `POST /transactions/deposit`: Caller must own the target deposit account.
* `POST /transactions/withdraw`: Caller must own the source withdrawal account.
* `POST /transactions/transfer`: Caller must own the source transfer account.

---

### 4. DTO-Based API Boundary & Request Validation

LedgerCore strictly decouples HTTP API payloads from internal JPA persistence entities (`@Entity`).

```text
Client Request JSON
        │
        ▼
[ Request DTO ] ──► (Jakarta Bean Validation @Valid)
        │
        ▼
[ Service Layer Mapping ] ──► (Converts DTO to Account/Transaction Entity)
        │
        ▼
[ PostgreSQL Entity ] ──► (Persisted with @Version & ACID constraints)
        │
        ▼
[ Response DTO ] ──► (Sanitized Output projection returned to Client)
```

#### Engineering Benefits of DTO Separation
1. **Mass Assignment Prevention**: Clients cannot inject internal fields (e.g., forcing `balance: 999999` or altering `version`).
2. **Credential Protection**: Prevents `User` BCrypt password hashes or internal keys from leaking in JSON responses.
3. **Circular Reference Prevention**: Prevents infinite recursion during Jackson JSON serialization of bidirectional `@ManyToOne`/`@OneToMany` JPA relationships.

#### DTO Inventory

| Request DTO | Response DTO | Primary Validation Annotations Applied |
| :--- | :--- | :--- |
| `CreateCustomerRequest` | `CustomerResponse` | `@NotBlank`, `@Size`, `@Pattern(regexp = "^[0-9]{10}$")`, `@Email` |
| `CreateUserRequest` | `UserResponse` | `@NotBlank`, `@Size(min = 4, max = 50)` |
| `CreateAccountRequest` | `AccountResponse` | `@NotNull` (`Currency`) |
| `DepositRequest` | `TransactionResponse` | `@NotNull`, `@DecimalMin("0.01")`, `@NotBlank`, `@Size(max = 100)` |
| `WithdrawalRequest` | `TransactionResponse` | `@NotNull`, `@DecimalMin("0.01")`, `@NotBlank`, `@Size(max = 100)` |
| `TransferRequest` | `TransactionResponse` | `@NotNull`, `@DecimalMin("0.01")`, `@NotBlank`, `@Size(max = 100)` |

#### Input Validation vs. Business Validation

```text
Input Validation (@Valid DTO Boundary)
  └── Is the request structurally valid? (e.g., amount > 0.00, phone has 10 digits, idempotencyKey non-blank)
  └── Failure Result: HTTP 400 Bad Request (handled by GlobalExceptionHandler)

Business Validation (Service Layer)
  └── Is the operation financially legal? (e.g., balance >= amount, currency matches, account is ACTIVE)
  └── Failure Result: HTTP 400 / 409 / 422 depending on domain rule
```

#### Validation Error Example
If a client sends an invalid request:
```json
{
  "sourceAccountId": 101,
  "destinationAccountId": 202,
  "amount": -100.00,
  "currency": "INR",
  "idempotencyKey": ""
}
```

LedgerCore catches `MethodArgumentNotValidException` in `GlobalExceptionHandler` and returns HTTP 400 Bad Request:
```json
{
  "status": 400,
  "message": "amount: Transfer amount must be greater than zero, idempotencyKey: Idempotency key cannot be blank",
  "timestamp": "2026-09-06T18:30:00"
}
```

---

### 5. Centralized Error Handling & Exception Architecture

Application exceptions are caught centrally by `GlobalExceptionHandler` (`@RestControllerAdvice`) and converted into a standard JSON error structure (`ErrorResponse` record).

```json
{
  "status": 404,
  "message": "Account not found with ID: 999",
  "timestamp": "2026-09-06T18:30:00"
}
```

#### Exception Inventory & HTTP Status Mapping

| Exception Class | Trigger Condition | HTTP Status Code | Response Description |
| :--- | :--- | :--- | :--- |
| `CustomerNotFoundException` | Customer ID does not exist in DB | `404 NOT FOUND` | Customer resource absent |
| `AccountNotFoundException` | Account ID does not exist in DB | `404 NOT FOUND` | Account resource absent |
| `TransactionNotFoundException` | Transaction ID does not exist in DB | `404 NOT FOUND` | Transaction resource absent |
| `AccessDeniedException` | User does not own requested account | `403 FORBIDDEN` | Unauthorized resource access |
| `MethodArgumentNotValidException` | DTO `@Valid` constraints violated | `400 BAD REQUEST` | Comma-separated field validation errors |
| `InsufficientFundsException` | Source account balance < transfer amount | `400 BAD REQUEST` | Business rule validation failure |
| `InvalidTransferException` | Transfer to same account or key conflict | `400 BAD REQUEST` | Invalid transfer parameters |
| `InvalidWithdrawalException` | Withdrawal business rule failure | `400 BAD REQUEST` | Invalid withdrawal parameters |
| `DuplicateUsernameException` | Username already registered in DB | `409 CONFLICT` | Resource creation conflict |
| `IllegalStateException` | Incompatible state (e.g., frozen account) | `409 CONFLICT` | Invalid state transition |
| `OptimisticLockException` | Concurrent update version collision | `409 CONFLICT` | Concurrent modification conflict |

---

### 6. Layered Architectural Responsibility Matrix

| Architectural Layer | Core Security / API Question | Primary Responsibility | Implementation Class |
| :--- | :--- | :--- | :--- |
| **Authentication** | *Who are you?* | Verifies client HTTP Basic credentials | `SecurityConfig`, `DaoAuthenticationProvider`, `CustomUserDetailsService` |
| **Authorization** | *Are you allowed?* | Verifies account ownership (`user.customer == account.customer`) | `AccountAuthorizationService`, Spring Security |
| **DTO Validation** | *Is the request well-formed?* | Enforces structural constraints on API payloads | Jakarta Bean Validation (`@Valid`), `GlobalExceptionHandler` |
| **Business Validation** | *Is the operation financially legal?* | Enforces balance, status, currency, and idempotency rules | `TransactionServiceImpl`, `DepositServiceImpl`, `WithdrawalServiceImpl` |
| **Transaction Atomicity** | *Can the operation commit safely?* | Enforces ACID state changes and outbox event persistence | PostgreSQL `@Transactional` boundary, Hibernate `@Version` |

---

### 7. Financial Authorization Execution Flow (`POST /transactions/transfer`)

```text
POST /transactions/transfer
        │
        ▼
1. Spring Security Authentication (Verify HTTP Basic BCrypt credentials)
        │
        ▼
2. Request DTO Validation (@Valid TransferRequest constraints)
        │
        ▼
3. Source Account Lookup & Ownership Guard (AccountAuthorizationService.isOwner)
        │  └── IF false ──► Throw AccessDeniedException (HTTP 403 Forbidden)
        ▼
4. Destination Account Lookup & Account Status Verification (Must be ACTIVE)
        │
        ▼
5. Currency & Idempotency Verification (lockIdempotencyKey)
        │
        ▼
6. Balance Sufficiency Check (balance >= amount)
        │  └── IF false ──► Throw InsufficientFundsException (HTTP 400 Bad Request)
        ▼
7. Single PostgreSQL Transaction Execution (Debits, Credits, Ledger Entries, Outbox Events)
        │
        ▼
8. Database Transaction Commit & Asynchronous Outbox Relay
```

---

### 8. Automated Security & Authorization Test Coverage

LedgerCore's authorization model is verified by automated integration tests in `AccountAuthorizationTest.java`:

* **`shouldAllowTransferFromOwnedAccount()`**: Verifies that an authenticated user (`userA`) can successfully transfer funds from an account belonging to their own customer record.
* **`shouldDenyTransferFromUnownedAccount()`**: Verifies that an authenticated user (`userA`) attempting to transfer funds from an account belonging to a different customer (`customer 2`) is rejected with an `AccessDeniedException` (HTTP 403) and that the source account balance remains completely unmodified.

---

### 9. Security Design Principles

* **Authentication Before Protected Operations**: All financial, account, and transaction endpoints require authenticated HTTP Basic credentials.
* **Resource Ownership Verification**: Knowing a target account ID is insufficient; the system explicitly verifies that the authenticated user owns the account before performing reads or mutations.
* **DTO Boundary Isolation**: Database entities (`@Entity`) are never exposed via REST APIs, preventing mass assignment vulnerabilities and credential leaks.
* **Declarative Input Validation**: Inputs are validated at the API boundary using Jakarta Bean Validation before reaching domain logic.
* **Centralized Exception Handling**: All runtime exceptions are mapped to consistent HTTP status codes and structured `ErrorResponse` payloads by `GlobalExceptionHandler`.
* **Fail-Safe Financial Transactions**: All state changes occur inside atomic database transactions, ensuring that failed operations roll back without leaving partial mutations.
* **Automated Security Verification**: Authorization enforcement is continuously validated by integration test suites (`AccountAuthorizationTest`).

---

## 🔄 Event-Driven Architecture

LedgerCore uses an **event-driven architecture** to decouple the synchronous financial transaction processing inside PostgreSQL from heavy, downstream read-side balance projections in Redis and external system integrations.

```text
HTTP Request
     ↓
Financial Transaction
     ↓
PostgreSQL ACID Commit
     ↓
Transactional Outbox
     ↓
Outbox Relay (SKIP LOCKED)
     ↓
Apache Kafka Streaming (ledgercore-transactions)
     ↓
Kafka Consumer (ledgercore-consumer)
     ↓
Event Processing & ProcessedEvent Check
     ↓
Redis Read Projection (Atomic Lua Script)
```

### Architectural Separation of Roles

| Layer | Technology | Architectural Role & Consistency Properties |
| :--- | :--- | :--- |
| **System of Record (SOR)** | **PostgreSQL** | Authoritative financial source of truth. Stores immutable ledger entries, transactions, account balances, and transactional outbox events with **Strong Consistency (ACID)**. |
| **Event Transport** | **Apache Kafka** | Asynchronous event streaming platform. Provides durable, multi-partition topic distribution (`ledgercore-transactions`) with **At-Least-Once Delivery** and per-account ordering. |
| **Read Projection** | **Redis** | In-memory key-value cache (`ledgercore:account:{id}:balance`). Provides low-latency balance reads with **Eventually Consistent** projections guarded by atomic Lua scripts. |

---

## 📦 Transactional Outbox Pattern & Relay Engine

### The Dual-Write Problem & Atomic Solution

In naive event-driven banking applications, publishing to a message broker directly within a service method introduces **dual-write anomalies**:

```text
WITHOUT TRANSACTIONAL OUTBOX (Dual-Write Risk):
PostgreSQL COMMIT ──► Application Crashes ──X──► Kafka Publish Fails
Result: Money is debited in PostgreSQL, but no Kafka event is published. Downstream projections and event listeners become permanently inconsistent!

IMPLEMENTED TRANSACTIONAL OUTBOX PATTERN:
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ PostgreSQL @Transactional Boundary                                                    │
│                                                                                        │
│ ├── 1. Mutate Account Balances                                                         │
│ ├── 2. Save Ledger Entries & Transaction Record                                        │
│ └── 3. Insert OutboxEvent (outbox_events table)                                       │
└────────────────────────────────────────────────────────────────────────────────────────┘
                                           │
                           COMMIT (All) or ROLLBACK (None)
                                           │
                                           ▼
                       OutboxRelayService (SKIP LOCKED Polling)
                                           │
                                           ▼
                               Published to Apache Kafka
```

### Outbox Entity Schema (`OutboxEvent`)

The `outbox_events` table in PostgreSQL stores event records persisted in the same transaction as financial operations:

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false, length = 50)
    private String eventType;             // e.g. TRANSFER_SOURCE_DEBITED, DEPOSIT_COMPLETED

    @Column(nullable = false)
    private Long aggregateId;             // Account ID (used as Kafka partition key)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;                 // JSON representation of event payload

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;     // PENDING, PROCESSING, SENT, FAILED

    @Column(nullable = false)
    private LocalDateTime createdAt;      // Creation timestamp

    private LocalDateTime publishedAt;    // Kafka publication confirmation timestamp
    private int retryCount;               // Number of publication failure attempts
    private LocalDateTime nextAttempt;    // Backoff retry scheduling timestamp
    private LocalDateTime processingStartedAt; // Timestamp when relay claimed event
    private String lastError;             // Exception stack trace or failure message
}
```

### Outbox Status Lifecycle & Stale Processing Recovery

```text
       ┌───────────┐
       │  PENDING  │ ◄──────────────────────────────┐
       └─────┬─────┘                                │
             │ Claim Batch                          │ Stale Event Recovery
             ▼ (SKIP LOCKED)                        │ (processingStartedAt > 5m)
       ┌───────────┐                                │
       │ PROCESSING│ ─────────────┐                 │
       └─────┬─────┘              │ Failure         │
             │                    ▼ (retryCount < 5)│
             │ Kafka ACK    ┌───────────┐           │
             │              │  PENDING  ├───────────┘
             ▼              └─────┬─────┘
       ┌───────────┐              │
       │   SENT    │              │ Retry Exceeded (retryCount >= 5)
       └───────────┘              ▼
                            ┌───────────┐
                            │  FAILED   │
                            └───────────┘
```

1. **`PENDING`**: Initial state when created inside financial `@Transactional` boundary.
2. **`PROCESSING`**: Claimed by `OutboxRelayService`. Timestamp `processingStartedAt` is set to track in-flight publishing.
3. **`SENT`**: Successfully published to Kafka and acknowledged by broker (`publishedAt` timestamp recorded).
4. **`FAILED`**: Terminal outbox failure state entered after exceeding maximum publication retries (5 attempts).
5. **Stale Processing Recovery**: If a relay node crashes while publishing, events stuck in `PROCESSING` state for longer than 5 minutes (`processingStartedAt < now - 5m`) are automatically reset back to `PENDING` by `OutboxRelayService` for re-attempted claim.

### Atomic Claiming with `FOR UPDATE SKIP LOCKED`

To enable high-throughput horizontal scaling of relay workers without thread blocking or row lock contention, `OutboxEventRepository` uses PostgreSQL `SKIP LOCKED`:

```sql
SELECT * FROM outbox_events 
WHERE status = 'PENDING' AND next_attempt <= NOW() 
ORDER BY event_id ASC 
LIMIT 100 
FOR UPDATE SKIP LOCKED;
```

* **Non-Blocking Worker Scaling**: Multiple concurrent `OutboxRelayService` instances can execute this query simultaneously. PostgreSQL automatically skips rows locked by competing worker threads, allowing each relay node to claim a distinct batch of 100 pending events without waiting.

### Exponential Backoff & Publication Retries

When Kafka publication experiences transient network errors, `OutboxRelayService` updates the outbox record using exponential backoff:

$$\text{delaySeconds} = \text{initialDelay} \times (\text{multiplier}^{\text{retryCount}})$$

* **Initial Delay**: 5 seconds.
* **Multiplier**: 2.0.
* **Max Attempts**: 5 total publication attempts.
* **Backoff Schedule**: Attempt 1 (Immediate), Attempt 2 (+5s), Attempt 3 (+10s), Attempt 4 (+20s), Attempt 5 (+40s).
* **Terminal Failure**: Upon the 5th consecutive failure, the outbox event status updates to `FAILED` with the last error stack trace saved to `lastError`.

---

## 📨 Apache Kafka Streaming & Consumer Reliability

### Producer Reliability & Durability Settings

`KafkaConfig` configures the `KafkaTemplate` producer with production-grade reliability parameters:

* **`acks = all` (-1)**: Kafka broker requires confirmation from all in-sync replicas (ISR) before acknowledging publication, preventing data loss during broker failovers.
* **`enable.idempotence = true`**: Enables Kafka producer-level idempotence to prevent duplicate messages caused by broker retry ACKs.
* **`retries = 3`**: Automatically retries transient network delivery errors up to 3 times.
* **`max.in.flight.requests.per.connection = 5`**: Maintains high throughput while enforcing message ordering when producer idempotence is enabled.

### Kafka Event Envelope (`KafkaEvent`) & Payload Schemas

Events are wrapped in a generic event envelope `KafkaEvent<T>` containing aggregate metadata:

```json
{
  "eventId": 1052,
  "eventType": "TRANSFER_SOURCE_DEBITED",
  "aggregateId": 101,
  "occurredAt": "2026-09-06T18:30:00",
  "payload": {
    "transactionId": 501,
    "sourceAccountId": 101,
    "destinationAccountId": 202,
    "amount": 1000.00,
    "currency": "INR",
    "balanceAfter": 4000.00,
    "accountVersion": 11,
    "idempotencyKey": "TX-AUTH-9901"
  }
}
```

### Per-Account Partition Ordering & Dual Transfer Events

Kafka guarantees message ordering **only within an individual topic partition**. Routing events by key ensures all events with the same key land on the exact same partition:

$$\text{Partition} = \text{hash}(\text{KafkaKey}) \pmod{\text{TotalPartitions}}$$

#### Multi-Account Ordering Solution
Standard single-event transfers (emitting one event keyed by `sourceAccountId`) break partition ordering for destination accounts. LedgerCore solves this by emitting **dual per-account transfer outbox events**:

1. **`TRANSFER_SOURCE_DEBITED`**:
   - `aggregateId` = `sourceAccountId` $\rightarrow$ Kafka Key = `"101"`
   - Routes to Partition $P_{source}$. Guarantees ordered Redis balance updates for Source Account 101.
2. **`TRANSFER_DESTINATION_CREDITED`**:
   - `aggregateId` = `destinationAccountId` $\rightarrow$ Kafka Key = `"202"`
   - Routes to Partition $P_{dest}$. Guarantees ordered Redis balance updates for Destination Account 202.

> ℹ️ **Global vs. Per-Account Ordering**: LedgerCore explicitly prioritizes **per-account ordering** over global topic ordering. Total cross-account ordering is neither required nor desirable in high-throughput banking systems.

### Event Payload Versioning & Monotonic Redis Guard

Event payloads include `balanceAfter` and the post-flush JPA `accountVersion`. Downstream consumers pass this sequence number to Redis to ensure that stale out-of-order event deliveries cannot regress newer balance projections.

### Durable Consumer Idempotency (`ProcessedEvent`)

Kafka's **at-least-once delivery** guarantee means consumers may receive duplicate messages during network re-balances. `EventProcessingService` tracks consumed event IDs in PostgreSQL `processed_events`:

```text
Kafka Consumer Polls Event (eventId = 1052)
        │
        ▼
EventProcessingService.isEventProcessed(1052)
        │
        ├─► Found in processed_events table?
        │      └── YES ──► Log duplicate & skip processing immediately (Idempotent Return)
        │
        └─► NOT Found in processed_events?
               └── NO ───► 1. Execute Redis Atomic Lua Version Guard
                          2. Insert ProcessedEvent(1052) in @Transactional(REQUIRES_NEW)
                          3. Complete Processing
```

### Consumer Retry Policy & Dead-Letter Topic (DLT)

Consumer errors are handled by Spring Kafka `DefaultErrorHandler`:

```text
Initial Consumer Attempt (Fails)
        │
        ▼ (1,000ms Fixed BackOff)
Retry Attempt 1 (Fails)
        │
        ▼ (1,000ms Fixed BackOff)
Retry Attempt 2 (Fails - 3 Total Tries Exceeded)
        │
        ▼
DeadLetterPublishingRecoverer
        │
        ▼
Published to Dead-Letter Topic: ledgercore-transactions.DLT
(Preserves original key, payload, and exception headers)
```

* **Retry Policy**: 3 total processing attempts (1 initial try + 2 retries with 1,000ms fixed backoff).
* **DLT Forwarding**: `DeadLetterPublishingRecoverer` routes permanently failing records to `ledgercore-transactions.DLT`.
* **Header Preservation**: DLT records retain original Kafka keys, payloads, cause exception names, and stack traces in record headers for operational inspection.

---

## ⚡ Redis Read-Side Balance Projections & Reconciliation

LedgerCore uses Redis as an **eventually consistent read-side projection layer**, optimizing account balance queries (`GET /accounts/{accountId}`) to sub-millisecond latency.

```text
PostgreSQL (Authoritative DB) ──► Outbox Relay ──► Kafka ──► Consumer ──► Atomic Lua Guard ──► Redis Projection
```

### Redis Key Structure

* `ledgercore:account:{accountId}:balance`: Stores projected account balance (`BigDecimal` string).
* `ledgercore:account:{accountId}:version`: Stores current projected account version sequence number (`Long` string).

### Atomic Lua Script Version Guard (`setBalanceIfVersionGreater`)

Asynchronous network delivery cannot guarantee strictly sequential event arrival. LedgerCore executes an atomic Lua script (`setBalanceIfVersionGreater.lua`) in Redis to enforce monotonic version updates:

```lua
local balanceKey = KEYS[1]
local versionKey = KEYS[2]
local newBalance = ARGV[1]
local newVersion = tonumber(ARGV[2])

local currentVersionStr = redis.call('GET', versionKey)
local currentVersion = currentVersionStr and tonumber(currentVersionStr) or -1

if newVersion > currentVersion then
    redis.call('SET', balanceKey, newBalance)
    redis.call('SET', versionKey, tostring(newVersion))
    return 1
else
    return 0
end
```

#### Monotonic Guard Rules
* If `incomingVersion > currentRedisVersion`: Redis balance and version are updated atomically. Script returns `1`.
* If `incomingVersion <= currentRedisVersion`: Event is stale or duplicate. Redis rejects the update without modifying state. Script returns `0`.

---

### Operational Reconciliation Subsystem

Operational background services continuously monitor, audit, and repair state drift between PostgreSQL authoritative records and Redis projections.

```text
                                ReconciliationService
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
    Reconciliation Report        RedisOrphanCleanupService    AccountProjectionBackfill
   (Audits PG vs. Redis)        (Purges Unbacked Keys)        (Restores Missing Cache)
```

#### 1. State Reconciliation Audit (`ReconciliationService`)
Compares PostgreSQL account records against Redis projection keys and returns a `ReconciliationReport` categorizing account states into four explicit enums:

| Reconciliation Status | Description | Action Required |
| :--- | :--- | :--- |
| **`MATCH`** | PostgreSQL and Redis balances and versions agree exactly. | None (System Consistent ✅) |
| **`MISMATCH`** | Projection exists in Redis, but balance or version differs from PostgreSQL authority. | Flagged for audit. |
| **`MISSING_FROM_REDIS``** | Account exists in PostgreSQL, but projection is missing from Redis cache. | Eligible for Backfill. |
| **`ORPHANED_IN_REDIS`** | Key exists in Redis, but no corresponding `Account` row exists in PostgreSQL. | Eligible for Cleanup. |

#### 2. Redis Orphan Cleanup Service (`RedisOrphanCleanupService`)
Safely identifies and purges stale Redis keys that lack PostgreSQL entity backing (`ORPHANED_IN_REDIS`).
* **Safety Verification**: Checks `AccountRepository.existsById(accountId)` before deleting keys. Projections backed by valid PostgreSQL accounts are preserved.

#### 3. Projection Backfill Service (`AccountProjectionBackfillService`)
Restores missing or corrupted Redis balance projections directly from authoritative PostgreSQL database records.
* `backfillAccount(Long accountId)`: Rebuilds Redis balance and version projection for a single account.
* `backfillAllMissingProjections()`: Audits all active PostgreSQL accounts and rebuilds missing Redis projections.
* `backfillAccounts(List<Long> accountIds)`: Batch-rebuilds projections for a target list of account IDs.

#### 4. Scheduled Periodic Reconciliation (`ReconciliationScheduler`)
Runs continuous, non-blocking background audits to ensure projection consistency:
```java
@Scheduled(fixedDelay = 30000) // Audits state every 30 seconds
public void runPeriodicReconciliation() {
    reconciliationService.reconcileAllAccounts();
}
```

---

## 🚨 System Stage Boundaries, Consistency Properties & Failure Model

LedgerCore enforces explicit consistency properties and recovery mechanisms across four system stages:

```text
[ Client Request ] ──► [ Stage 1: PostgreSQL ACID ] ──► [ Stage 2: Outbox Relay ] ──► [ Stage 3: Kafka Streaming ] ──► [ Stage 4: Redis Projection ]
                       (Strong Consistency)            (Transactional Boundary)       (At-Least-Once Delivery)        (Eventually Consistent)
```

### Failure Boundaries & Operational Recovery Matrix

| System Failure Scenario | Operational Handling & Recovery Boundary | System State After Failure |
| :--- | :--- | :--- |
| ❌ **PostgreSQL Financial Tx Fails** | Entire transaction (balances, ledger entries, outbox record) rolls back cleanly. | Zero side effects. No Kafka event published. |
| ❌ **Outbox Persistence Fails** | Exception inside `@Transactional` causes complete rollback of DB operations. | Zero side effects. Account balance unchanged. |
| ❌ **Kafka Broker Temporarily Down** | `OutboxRelayService` catches exception, updates outbox to `PENDING` with backoff. | Outbox events remain in PostgreSQL DB for next poll. |
| ❌ **Outbox Publication Max Retries Exceeded** | Outbox event status updated to `FAILED` after 5 failed attempts. | Logged for manual operational inspection. |
| ❌ **Outbox Relay Node Crashes Mid-Publish** | Events stuck in `PROCESSING` > 5 mins automatically reset to `PENDING`. | Stale events re-claimed by active worker node. |
| ❌ **Kafka Consumer Processing Fails** | `DefaultErrorHandler` triggers 3 attempts (1 initial + 2 retries with 1s backoff). | Transient errors recovered automatically. |
| ☠️ **Consumer Permanently Fails (3 Tries)** | `DeadLetterPublishingRecoverer` routes event to `ledgercore-transactions.DLT`. | Event isolated in DLT with exception headers. |
| 🔁 **Duplicate Kafka Delivery Occurs** | `EventProcessingService` checks `processed_events` table by `eventId`. | Duplicate ignored without duplicate execution. |
| ⏳ **Stale / Out-of-Order Event Arrives** | Atomic Lua script compares incoming `@Version` against current Redis version. | Stale update rejected. Redis state preserved. |
| ⚡ **Redis Temporarily Unavailable** | Connection failure triggers Spring Kafka retry policy (3 attempts). | If Redis stays down, event routes to DLT. |
| 🔍 **Redis Projection Missing / Drifting** | Operational `ReconciliationService` and `AccountProjectionBackfillService` audit and rebuild. | Redis projection restored from PostgreSQL authority. |

---

## 🧪 Testing & Reliability

LedgerCore's correctness is validated by a suite of **50 automated integration, concurrency, idempotency, outbox, Kafka, Redis, and reconciliation tests**.

```text
[INFO] Results:
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Complete Test Coverage Inventory

| Test Class Name | Subsystem / Focus Area | Verified Engineering Guarantee |
| :--- | :--- | :--- |
| `OutboxAtomicityTest` | Transactional Outbox | Confirms outbox events and financial state commit/rollback together atomically. |
| `DepositOutboxTest` | Deposit Lifecycle | Verifies deposit balance increases, ledger entry generation, and outbox event persistence. |
| `WithdrawalOutboxTest` | Withdrawal Lifecycle | Verifies withdrawal balance decreases, insufficient funds rejection, and clean rollback. |
| `TransferOutboxTest` | Transfer Lifecycle | Verifies source debit, destination credit, balanced ledger entries, and dual outbox creation. |
| `OutboxRepositoryTest` | Outbox Relaying | Verifies `SKIP LOCKED` query execution and outbox status transitions. |
| `OutboxConcurrentClaimTest` | Outbox Concurrency | Validates concurrent relay workers claiming distinct outbox batches without duplicate processing. |
| `OutboxStaleRecoveryTest` | Outbox Recovery | Verifies stuck `PROCESSING` outbox events automatically reset to `PENDING` after timeout. |
| `AccountConcurrencyTest` | JPA Optimistic Locking | Validates `@Version` optimistic locking under concurrent single-account operations. |
| `TransferConcurrencyTest` | Concurrent Transfers | Validates optimistic lock collision detection and rollback during concurrent transfers. |
| `DepositIdempotencyTest` | Deposit Idempotency | Confirms duplicate deposit requests with identical key return original response without re-crediting. |
| `WithdrawalIdempotencyTest` | Withdrawal Idempotency | Confirms duplicate withdrawal requests with identical key return original response without re-debiting. |
| `TransferIdempotencyTest` | Transfer Idempotency | Confirms duplicate transfer requests with identical key return original response without re-transferring. |
| `ConcurrentDepositIdempotencyTest` | Concurrent Idempotency | Validates DB advisory locks serializing concurrent identical deposit requests. |
| `ConcurrentWithdrawalIdempotencyTest`| Concurrent Idempotency | Validates DB advisory locks serializing concurrent identical withdrawal requests. |
| `ConcurrentIdempotencyTest` | Concurrent Idempotency | Validates DB advisory locks serializing concurrent identical transfer requests. |
| `LedgerIntegrityTest` | Double-Entry Ledger | Verifies total debits equal total credits for all completed transactions. |
| `TransactionAtomicityTest` | Database Boundaries | Verifies PostgreSQL `@Transactional` rollback on database exceptions. |
| `KafkaEventOrderingTest` | Kafka Single Account | Verifies single-account events land in strict sequence on identical partitions. |
| `KafkaMultiAccountOrderingTest` | Kafka Multi-Account | Verifies dual transfer events route by aggregate account key for ordered partition streaming. |
| `KafkaConsumerIdempotencyTest` | Consumer Idempotency | Verifies `ProcessedEvent` tracking table preventing duplicate Kafka message execution. |
| `ConcurrentKafkaConsumerIdempotencyTest`| Consumer Concurrency | Validates `REQUIRES_NEW` transaction processing for concurrent duplicate consumer events. |
| `KafkaConsumerRetryAndDltTest` | Consumer Error Recovery | Verifies 3-attempt retry policy, `ProcessedEvent` tracking, and Dead-Letter Topic (DLT) forwarding. |
| `AccountBalanceRedisServiceTest` | Redis Projection | Validates atomic Lua script execution (`setBalanceIfVersionGreater`) and version checking. |
| `ReconciliationTest` | State Audit Engine | Verifies detection of `MATCH`, `MISMATCH`, `MISSING_FROM_REDIS`, and `ORPHANED_IN_REDIS`. |
| `RedisOrphanCleanupTest` | Projection Safety | Verifies safe detection and purging of stale Redis keys missing PostgreSQL entity backing. |
| `AccountProjectionBackfillTest` | Projection Recovery | Verifies operational rebuilding of missing Redis balance projections from PostgreSQL authority. |
| `AccountAuthorizationTest` | Ownership Security | Verifies authorization guard permitting owned account transfers and rejecting unowned attempts (HTTP 403). |
| `LedgerCoreApplicationTests` | Context Loading | Verifies complete Spring Boot application context initialization. |

---

## 📡 REST API Reference

### 👤 Customer Endpoints

| Method | Endpoint | Description | Auth Required | Status Codes |
| --- | --- | --- | --- | --- |
| `POST` | `/customer` | Register a new banking customer | No | `201 Created`, `400 Bad Request` |
| `GET` | `/customer` | Fetch all customers (Admin) | Yes (`ADMIN`) | `200 OK`, `401 Unauthorized` |
| `GET` | `/customer/{id}` | Get customer profile details | Yes | `200 OK`, `403 Forbidden`, `404 Not Found` |
| `PUT` | `/customer/{id}` | Update customer contact info | Yes | `200 OK`, `400 Bad Request`, `404 Not Found` |
| `DELETE` | `/customer/{id}` | Deactivate customer record | Yes (`ADMIN`) | `204 No Content`, `404 Not Found` |

### 🔑 User & Authentication Endpoints

| Method | Endpoint | Description | Auth Required | Status Codes |
| --- | --- | --- | --- | --- |
| `POST` | `/customer/{id}/users` | Create login credentials for customer | Yes | `201 Created`, `409 Duplicate Username` |

### 💳 Account Endpoints

| Method | Endpoint | Description | Auth Required | Status Codes |
| --- | --- | --- | --- | --- |
| `POST` | `/customer/{id}/accounts` | Open a new bank account (Currency specified) | Yes | `201 Created`, `400 Bad Request` |
| `GET` | `/accounts/{accountId}` | Fetch balance & account status (Redis cached) | Yes (Owner) | `200 OK`, `403 Forbidden`, `404 Not Found` |
| `PATCH` | `/accounts/{id}/freeze` | Freeze an account temporarily | Yes | `200 OK`, `409 Invalid State Transition` |
| `PATCH` | `/accounts/{id}/close` | Permanently close an account | Yes | `200 OK`, `409 Conflict` |

### 💸 Financial Transaction Endpoints

| Method | Endpoint | Description | Auth Required | Status Codes |
| --- | --- | --- | --- | --- |
| `POST` | `/transactions/deposit` | Deposit funds into an active account | Yes (Owner) | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `POST` | `/transactions/withdraw` | Withdraw funds from an active account | Yes (Owner) | `201 Created`, `400 Bad Request`, `422 Unprocessable` |
| `POST` | `/transactions/transfer` | Transfer funds between two active accounts | Yes (Owner) | `201 Created`, `400 Bad Request`, `409 Conflict` |
| `GET` | `/transactions/accounts/{accountId}` | Fetch all transactions for an account | Yes (Owner) | `200 OK`, `403 Forbidden`, `404 Not Found` |
| `GET` | `/transactions/{transactionId}` | Get details of a specific transaction | Yes (Participant) | `200 OK`, `403 Forbidden`, `404 Not Found` |
| `GET` | `/transactions/{transactionId}/ledger` | Fetch ledger entries for a transaction | Yes (Participant) | `200 OK`, `403 Forbidden`, `404 Not Found` |

---

## 📂 Project Directory Structure

```text
src/main/java/com/example/ledgercore/
├── config/
│   ├── KafkaConfig.java                 # Kafka producer/consumer, DLT recoverer, and retry policies
│   ├── RedisConfig.java                 # Redis template and atomic Lua script configuration
│   ├── SecurityConfig.java              # Spring Security filter chain & password encoders
│   └── SystemAccountInitializer.java   # System ledger account initialization
├── controller/
│   ├── AccountController.java           # Account management & status endpoints
│   ├── CustomerController.java          # Customer management endpoints
│   ├── DepositController.java           # Deposit financial endpoint
│   ├── TransactionController.java       # Transfer, withdrawal, and audit query endpoints
│   └── UserController.java              # User registration & auth endpoints
├── dto/
│   ├── request/                         # Strongly validated client input DTOs
│   │   ├── CreateAccountRequest.java
│   │   ├── CreateCustomerRequest.java
│   │   ├── CreateUserRequest.java
│   │   ├── DepositRequest.java
│   │   ├── TransferRequest.java
│   │   └── WithdrawalRequest.java
│   └── response/                        # Sanitized public API output DTOs
│       ├── AccountResponse.java
│       ├── CustomerResponse.java
│       ├── LedgerEntryResponse.java
│       ├── TransactionResponse.java
│       └── UserResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java      # Centralized @RestControllerAdvice error mapping
│   ├── AccountNotFoundException.java
│   ├── CustomerNotFoundException.java
│   ├── InsufficientFundsException.java
│   └── OptimisticLockingException.java
├── kafka/
│   ├── EventProcessingService.java      # Durable consumer idempotency & Lua projection execution
│   ├── KafkaConsumerService.java        # Spring Kafka listener for transaction events
│   ├── KafkaProducerService.java        # Kafka publishing wrapper
│   └── payload/                         # Strongly typed event payloads
│       ├── DepositEventPayload.java
│       ├── TransferEventPayload.java
│       └── WithdrawalEventPayload.java
├── model/
│   ├── Account.java                     # Account JPA Entity with @Version
│   ├── AccountStatus.java               # ACTIVE, FROZEN, CLOSED
│   ├── Currency.java                    # INR, USD, EUR, GBP
│   ├── Customer.java                    # Customer JPA Entity
│   ├── LedgerEntry.java                 # Ledger entry JPA Entity (DEBIT, CREDIT)
│   ├── LedgerEntryType.java             # DEBIT, CREDIT
│   ├── Transaction.java                 # Financial transaction entity with idempotency key
│   ├── TransactionStatus.java           # COMPLETED
│   ├── TransactionType.java             # DEPOSIT, WITHDRAWAL, TRANSFER
│   └── User.java                        # User/Auth JPA Entity
├── outbox/
│   ├── OutboxEvent.java                 # Transactional outbox entity
│   ├── OutboxEventStatus.java           # PENDING, PROCESSING, SENT, FAILED
│   ├── OutboxEventRepository.java       # Repository with SKIP LOCKED polling queries
│   └── OutboxRelayService.java          # Background relay service polling outbox events
├── reconciliation/
│   ├── AccountProjectionBackfillService.java # Operational rebuilding of Redis balance projections
│   ├── ReconciliationService.java       # Detection of projection drift between PG and Redis
│   └── RedisOrphanCleanupService.java   # Purging stale Redis keys lacking PG entity backing
├── repository/
│   ├── AccountRepository.java
│   ├── CustomerRepository.java
│   ├── LedgerEntryRepository.java
│   ├── ProcessedEventRepository.java    # Consumer idempotency tracking repository
│   ├── TransactionRepository.java       # Includes DB advisory lock query (lockIdempotencyKey)
│   └── UserRepository.java
├── security/
│   ├── AccountAuthorizationService.java # Deep ownership verification logic
│   └── CustomUserDetailsService.java    # Database-backed user loading
└── service/
    ├── AccountBalanceRedisService.java  # Atomic Lua script execution for Redis balance projections
    ├── AccountService.java
    ├── CustomerService.java
    ├── DepositService / DepositServiceImpl.java
    ├── TransactionService / TransactionServiceImpl.java
    ├── UserService.java
    └── WithdrawalService / WithdrawalServiceImpl.java
```

---

## 🚀 Local Setup & Configuration

### Prerequisites

* **Java 17** or higher
* **Maven 3.8+**
* **PostgreSQL 14+**
* **Apache Kafka 3.x**
* **Redis 7.x**

### 1. Clone the Repository

```bash
git clone https://github.com/SulemanAG/LedgerCore-.git
cd LedgerCore-
```

### 2. Configure Environment Variables

Export local database and Redis credentials:

```bash
# Linux / macOS
export DB_URL=jdbc:postgresql://localhost:5432/ledgercore_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_secure_password
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Windows (PowerShell)
$env:DB_URL="jdbc:postgresql://localhost:5432/ledgercore_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_secure_password"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
```

### 3. Build and Run

```bash
# Run Maven tests first to verify suite
./mvnw clean test

# Launch application
./mvnw spring-boot:run
```

The server starts on `http://localhost:8081` by default.

---

## 🛡️ Core Engineering Guarantees

Based on the implemented source code and verified test suite, LedgerCore guarantees:

* **ACID Financial Mutations**: PostgreSQL commits financial state changes transactionally.
* **Double-Entry Balance Invariant**: Every operation generates matching `DEBIT` and `CREDIT` entries ($\sum \text{Debits} = \sum \text{Credits}$).
* **Optimistic Concurrency Control**: JPA `@Version` sequence locking rejects concurrent race conditions cleanly.
* **Idempotent Financial Requests**: Database advisory locks prevent duplicate transfers/deposits/withdrawals using identical idempotency keys.
* **Atomic Event Persistence**: Outbox events persist inside the exact same database transaction as the financial state change.
* **High-Throughput Outbox Relaying**: PostgreSQL `SKIP LOCKED` allows concurrent relay workers to publish events without row locking contention.
* **Ordered Per-Account Streaming**: Kafka messages keyed by aggregate account ID guarantee ordered partition processing for every account.
* **At-Least-Once Consumer Safety**: Consumer idempotency via PostgreSQL `processed_events` prevents duplicate message execution.
* **Stale Projection Protection**: Atomic Lua version scripts in Redis prevent out-of-order event deliveries from overwriting newer balance projections.
* **Operational Self-Healing**: Reconciliation, orphan cleanup, and backfill services continuously audit and repair state drift.

---

## 🎯 What This Project Demonstrates

LedgerCore demonstrates enterprise-grade financial systems engineering across multiple domain areas:

1. **Financial Domain Modeling**: Strict double-entry accounting, immutable transaction journals, decoupled identity vs. account containers.
2. **Distributed Systems Design**: Transactional outbox pattern, asynchronous event-driven streaming, eventual consistency boundaries.
3. **Concurrency Control**: Database-level advisory locks, JPA `@Version` optimistic locking, concurrent outbox polling with `SKIP LOCKED`.
4. **Messaging & Reliability**: Multi-partition Kafka streaming, per-account message ordering, bounded consumer retries, Dead-Letter Topic (DLT) forwarding.
5. **Data Consistency**: Atomic Lua script version guards in Redis, consumer idempotency tracking, operational state reconciliation engines.
6. **Security & API Design**: HTTP Basic authentication, ownership-based authorization guards, DTO input boundary validation, centralized exception handling.
7. **Comprehensive Automated Testing**: 50 automated integration, concurrency, ordering, idempotency, retry, DLT, and reconciliation tests.

---

## 📊 Performance Target & Architectural Scope

The LedgerCore architecture is designed to target **1,000+ Transactions Per Second (TPS)** by decoupling synchronous HTTP request execution from read projections through asynchronous outbox relaying, multi-partition Kafka topics, and in-memory Redis read caches.

> ℹ️ **Benchmark Status Note**: High throughput is a **design performance target** enforced by the architecture. Formal stress testing and throughput benchmarking using Apache JMeter are scheduled for the next development phase.

---

## 🔮 Future Hardening & Roadmap

Future architectural enhancements scheduled for upcoming engineering iterations:

* **Containerization & Orchestration**: Production Dockerfile and Kubernetes (k8s) deployment manifests.
* **Observability & Metrics**: Prometheus metrics collection and Grafana dashboards for Outbox relay lag, Kafka consumer lag, and Redis hit rates.
* **Distributed Tracing**: OpenTelemetry / Zipkin context propagation across HTTP requests, Outbox relay, Kafka messages, and Redis projections.
* **Database Migrations**: Flyway or Liquibase schema migration management replacing automatic Hibernate schema generation.
* **Automated Load Testing**: Apache JMeter test suite for formal 1,000+ TPS throughput and latency benchmarking under concurrent load.

---

## 👨‍💻 Author

**Suleman Agasimani**  
*Backend Engineering & Financial Systems Architecture*
