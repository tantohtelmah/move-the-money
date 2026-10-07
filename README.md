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
