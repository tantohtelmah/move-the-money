# Move the Money
A small full-stack money-transfer system built with Java, Spring Boot, Spring Data JPA, and PostgreSQL.
The application allows accounts to hold balances, transfer money between accounts, retrieve current balances, and view transaction history.

The implementation focuses on four correctness guarantees:

1. An account balance cannot become negative, including when transfers run concurrently.
2. A transfer is atomic: either the entire transfer succeeds or none of it does.
3. Repeated submissions of the same transfer are applied only once.
4. Monetary amounts are handled exactly without floating-point arithmetic.

## Technology
- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA / Hibernate
- PostgreSQL
- Maven
- JUnit 5

## Design

The application follows a simple layered structure:

HTTP Request -> Controller -> Service -> Repository -> PostgreSQL


### Accounts

An account contains:

- generated ID
- balance stored as `BigDecimal`

Balances are persisted using a decimal database column rather than floating-point values.

### Transfers

A transfer records:

- transfer ID
- idempotency key
- source account
- destination account
- amount
- creation timestamp

Transfers reference accounts using database foreign-key relationships.*

## Correctness Guarantees

1. No Negative Balances - Using pessimistic write locks.
2. Atomic Transfers -  Executed inside a Spring @Transactional method.
3. Idempotency - Idempotency-Key
4. Exact Monetary Amounts - BigDecimal, not double or float.


## API

### Open an Account

```http
POST /accounts
Content-Type: application/json
```

Example:

```json
{
  "startingBalance": 100.00
}
```

### Get Current Balance

```http
GET /accounts/{id}
```

Example:

```bash
curl http://localhost:8080/accounts/1
```

### Transfer Money

```http
POST /transfers
Content-Type: application/json
Idempotency-Key: transfer-001
```

Example:

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 25.00
}
```

Example using curl:

```bash
curl -X POST http://localhost:8080/transfers \
-H "Content-Type: application/json" \
-H "Idempotency-Key: transfer-001" \
-d '{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 25.00
}'
```

### Transaction History

```http
GET /accounts/{id}/transactions
```

Returns transfers where the account was either the sender or receiver, ordered with the most recent transfers first.

## Running the Application

### Prerequisites

Install:

- Java 21
- PostgreSQL

Verify Java:

```bash
java -version
```

Start PostgreSQL:

```bash
sudo service postgresql start
```

Create the database:

```sql
CREATE DATABASE move_the_money;
```

The default local configuration expects:

```text
Database: move_the_money
Username: postgres
Password: postgres
```

Then start the application:

```bash
./mvnw spring-boot:run
```

The API runs at:

```text
http://localhost:8080
```

## Running the Tests

Run:

```bash
./mvnw test
```

The automated tests cover:

- successful transfers
- insufficient funds
- atomic failure without partial balance changes
- duplicate/idempotent transfers
- monetary precision validation
- concurrent transfers competing for the same balance



## Running the Application

### Prerequisites

Install:
- Java 21
- PostgreSQL

Verify Java:
- java -version

Start PostgreSQL:
- sudo service postgresql start

Create the database:
- CREATE DATABASE move_the_money;


The default local configuration expects:
Database: move_the_money
Username: postgres
Password: {password}

Then start the application:

- ./mvnw spring-boot:run

The API runs at:
- http://localhost:8080

## Running the Tests
Run
- ./mvnw test

The automated tests cover:

- successful transfers
- insufficient funds
- atomic failure without partial balance changes
- duplicate/idempotent transfers
- monetary precision validation
- concurrent transfers competing for the same balance
- balanced transfer/opening ledger entries
- reconciliation success, mismatch, and legacy-account handling
- rollback on ledger posting failure and concurrent idempotent retries

Tests use an isolated PostgreSQL Testcontainer; Docker must be available when running
`./mvnw test`.

## Ledger and Reconciliation

Transfers post two immutable ledger entries in the same database transaction as the
transfer record and account balance updates: a debit to the source account and an
equal credit to the destination. Opening balances use a matching customer credit
and opening-equity debit. Customer balances are reconstructed as credits minus
debits; the `accounts.balance` column is retained as a fast, locked balance cache.
Transfers recalculate both locked accounts from the ledger before posting and stop
if either cached balance disagrees.

Verify a balance with:

```http
GET /accounts/{id}/reconciliation
```

The response includes only the account ID, stored balance, reconstructed ledger
balance, difference, and a status. `LEDGER_NOT_INITIALIZED` means the account
predates ledger posting; it is deliberately not reported as reconciled. Transfers
involving such accounts are blocked until a separately verified cutover initializes
their ledger. The additive migration preserves existing accounts and transfers and
does not fabricate historical entries.

Flyway migrations provide the schema (`V1` is the baseline for a fresh database;
existing non-empty schemas are baselined at version 1 and receive the additive
ledger migration). Tests run against an ephemeral PostgreSQL Testcontainer and do
not clear or connect to the configured development database.

Ledger entries have no application delete operation or mutable setters, and JPA
callbacks reject updates/deletes. Production deployments should additionally run
with a least-privilege database role that cannot update or delete ledger rows, and
enforce append-only behavior with database permissions and/or triggers. Reconciliation
is an operational check, not a substitute for access control or a verified legacy
data cutover.

## Deliberate Scope Decisions

This implementation intentionally prioritizes transfer correctness over additional product features.

The following were left out:

- frontend/UI
- authentication and authorization
- multiple currencies
- account owner/profile information
- deployment infrastructure
- microservices or messaging infrastructure
- production-grade API error responses
- production database migrations

For example, requesting a nonexistent account currently results in a generic server error rather than a polished `404` response. With additional time, I would add centralized exception handling and clearer API error responses.

## What I Would Add Next

Given more time, I would:

1. Add structured API error handling with appropriate HTTP status codes.
2. Separate API response DTOs from persistence entities.
3. Add production database migrations using Flyway or Liquibase.
4. Improve database configuration using environment variables.
5. Add additional concurrent idempotency tests.
6. Add containerized PostgreSQL integration testing for reproducible test environments.
7. add filter transaction history by date
