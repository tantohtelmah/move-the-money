# Build Log — Move the Money

## Approach

I focused the four-hour build on correctness rather than feature count.

I chose Java 21, Spring Boot, Spring Data JPA, and PostgreSQL. The application exposes a REST API for:

- opening an account with a starting balance
- retrieving an account balance
- transferring money between accounts
- retrieving transaction history

I intentionally did not build a frontend because the assignment allowed an HTTP API and the transfer invariants were more important within the time limit.

## Key Design Decisions

### PostgreSQL and Transactions

I used PostgreSQL as the shared source of truth and Spring's `@Transactional` support for transfers.

A transfer performs the balance check, debit, credit, and transfer recording inside one database transaction. If the operation fails, the transaction rolls back rather than leaving one side of the transfer applied.

### Exact Money

I used `BigDecimal` rather than `double` or `float`.

Balances and transfer amounts are stored with two decimal places. I also added validation that rejects transfer amounts containing more than two decimal places rather than silently rounding them.

### Concurrency

The most important design problem was preventing two simultaneous transfers from spending the same money.

I used pessimistic database locking when loading accounts for a transfer. The available balance is checked only after the account row has been locked.

I also lock the two accounts in deterministic ID order rather than sender/receiver order. This reduces the possibility of two opposite transfers acquiring the same locks in different orders and deadlocking.

I wrote a concurrency test where an account containing $100 receives two simultaneous requests to send $80 to different accounts. The test verifies that exactly one succeeds, the source ends with $20, and only $80 reaches the destination accounts.

### Idempotency

Each transfer accepts an idempotency key.

The transfer table has a uniqueness constraint on that key, and previously processed keys are checked before applying a transfer. My automated test submits the same transfer twice and verifies that the balances change only once and only one transfer record exists.

## How I Used AI

I used AI as a coding and reasoning assistant during the time-box.

I used it to:

- discuss the initial Spring Boot project structure
- explain unfamiliar Spring/JPA annotations while I implemented them
- reason about transaction boundaries and pessimistic locking
- review the account-locking order
- generate initial test structures
- help interpret compiler errors and failing test output
- discuss idempotency and database constraints
- help keep the implementation within the four-hour scope

I did not treat generated code as automatically correct. I reviewed the code, asked questions about design decisions, ran it, and used tests and database behaviour to verify it.

## Where AI Was Wrong or Incomplete

One useful example occurred while testing transfers.

The initial diagnosis of a failing successful-transfer test focused on whether Hibernate was persisting the balance updates correctly. After inspecting the actual service implementation and test behaviour, the problem turned out to be test isolation.

The test reused an idempotency key that already existed in the PostgreSQL database. The service therefore correctly treated the new request as an already processed transfer and did not modify the newly created test accounts.

I fixed this by adding test setup that deletes transfer records and accounts before each test. Transfers must be deleted first because they reference accounts through foreign keys.

This was a useful reminder not to accept the first AI diagnosis. The test output and actual database state were the source of truth.

## Suggestions I Rejected or Changed

### Plain Account IDs in Transfers

An early transfer model represented `fromAccountId` and `toAccountId` as plain `Long` values.

I questioned whether these were actually relational to the accounts table. Although the application would understand the IDs as account references, the database would not enforce that relationship.

I changed the model to JPA `@ManyToOne` relationships with foreign keys so that a transfer cannot reference a nonexistent account.

### Additional Features

I deliberately avoided adding features such as:

- a frontend
- authentication
- account profiles
- multiple currencies
- microservices
- messaging infrastructure
- deployment infrastructure
- extensive API error handling

These could improve a production system, but they would reduce the time available to prove the required financial invariants.

I added only a small Bash menu as a demonstration client because it makes the REST API easier to show without introducing frontend complexity.

## Problems Encountered

I encountered several implementation and environment issues during the build:

- Maven wrapper initially lacked execute permission.
- The installed Java version did not support the project's Java 21 target.
- PostgreSQL had to be configured for the application.
- A repository failed compilation because several JPA/Spring imports were missing.
- Adding transaction history initially caused a constructor-injection compilation error because `TransferService` had been declared as a final dependency but was not added to the controller constructor.
- Requests for deleted/nonexistent accounts produced generic HTTP 500 responses because centralized API exception handling was outside the implemented scope.
- Persistent test data initially interfered with idempotency testing, which led to adding database cleanup before each test.

## Current Discomforts / What I Would Improve

The main areas I would improve with more time are:

1. **Concurrent idempotency:** add stronger automated testing specifically for simultaneous requests using the same idempotency key.
2. **API errors:** map invalid requests and missing accounts to clear `400`, `404`, or `409` responses instead of generic `500` responses.
3. **DTO boundaries:** return dedicated API response DTOs instead of exposing persistence entities directly.
4. **Database migrations:** replace Hibernate schema updates with Flyway or Liquibase migrations.
5. **Test infrastructure:** use an isolated/containerized PostgreSQL instance so automated tests never interact with development data.
6. **Configuration:** move database configuration fully to environment variables for production-style configuration.

## Final Reflection

The main lesson from the exercise was that the difficult part of moving money is not subtracting from one balance and adding to another. The difficult part is preserving the invariants when requests fail, repeat, or happen concurrently.

Given the time constraint, I prioritized database transactions, locking, idempotency, exact monetary representation, and tests over additional features.

## Double-Entry Ledger Extension

The transfer workflow now writes two equal ledger lines (source debit and
destination credit) in the same transaction as the transfer and cached account
balance updates. Positive opening balances are posted as a customer credit matched
by an opening-equity debit. Ledger writes validate two-line balance and use database
uniqueness constraints to prevent duplicate lines; entity callbacks and a
read-oriented repository prevent application-level mutation/deletion.

`GET /accounts/{id}/reconciliation` independently totals customer credits minus
debits and compares that value with the stored balance. Transfers derive available
balances from the ledger while holding the existing account locks and reject a
transfer if a cached balance disagrees. Additive Flyway migrations
preserve existing data and explicitly leave pre-ledger accounts uninitialized;
they are reported as not reconciled and cannot transfer until a verified cutover
has been performed. PostgreSQL integration tests use an ephemeral Testcontainer,
never the configured development database.