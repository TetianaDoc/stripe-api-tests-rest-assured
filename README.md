# Stripe API Test Automation Framework

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![REST Assured](https://img.shields.io/badge/REST%20Assured-5.4.0-brightgreen.svg)](https://rest-assured.io/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.10.2-blue.svg)](https://junit.org/junit5/)
[![Allure Report](https://img.shields.io/badge/Allure%20Report-Live%20on%20GitHub%20Pages-brightgreen.svg)](https://tetianadoc.github.io/stripe-api-tests-rest-assured/)
[![Build Status](https://img.shields.io/badge/CI-GitHub%20Actions-blue.svg)](.github/workflows/maven.yml)

An enterprise-grade, scalable API test automation framework built for verifying [Stripe's REST API](https://stripe.com/docs/api) (`/v1/customers` and `/v1/products`). 

The framework leverages **Java 17**, **REST Assured 5.4**, **JUnit 5**, **Hamcrest**, and **Allure Reporting**, emphasizing test isolation, secret hygiene, centralized specification management, and robust handling of advanced HTTP scenarios such as cursor-based pagination and idempotency replays.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Key Features & Design Principles](#key-features--design-principles)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Environment Configuration](#environment-configuration)
- [Running Tests](#running-tests)
- [Allure Test Reporting](#allure-test-reporting)
- [Test Coverage & Scenarios](#test-coverage--scenarios)
- [Design Patterns & Best Practices](#design-patterns--best-practices)
- [Continuous Integration (CI/CD)](#continuous-integration-cicd)

---

## Overview

Stripe's REST API uses HTTP verbs, URL-encoded request bodies, JSON response payloads, and standard HTTP response codes. This framework demonstrates how to build maintainable and resilient automated tests against Stripe's real or sandboxed REST API while ensuring:

1. **Security**: Zero credential leakage in CI/CD console logs, Allure reports, and version control.
2. **Safety**: Fail-safe guards preventing accidental execution against live production Stripe accounts.
3. **Reliability**: Deterministic teardown and lifecycle hooks preventing orphaned resources in test accounts.
4. **Coverage**: Positive paths, validation errors (400), authentication failures (401), missing resources (404), pagination, and idempotency guarantees.

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 17 | Core programming language |
| **REST Assured** | 5.4.0 | Fluent HTTP client and API assertion DSL |
| **JUnit 5 (Jupiter)** | 5.10.2 | Test runner, lifecycle management (`@Test`, `@Order`, `@AfterEach`, `@AfterAll`) |
| **Hamcrest** | 2.x (transitive) | Expressive response matchers and payload assertions |
| **Jackson Databind** | 2.16.1 | JSON parsing and object serialization |
| **WireMock** | 3.3.1 | HTTP service virtualization, fault injection, and latency simulation |
| **Allure Framework** | 2.25.0 / 2.12.0 plugin | Rich HTML test reporting with AspectJ weaver |
| **Maven** | 3.8+ | Dependency management and build lifecycle |
| **GitHub Actions** | Ubuntu / Temurin JDK 21 | Automated continuous integration pipeline |

---

## Key Features & Design Principles

- **Centralized Specification Factory (`StripeSpecFactory`)**:
  - Reusable `RequestSpecification` configured with base URI (`https://api.stripe.com/v1`), URL-encoded content type (`ContentType.URLENC`), and OAuth2 Bearer authentication.
  - Reusable `ResponseSpecification` validating HTTP `200 OK` and JSON response type (`ContentType.JSON`).
  - Single-point maintenance for environment configurations.

- **Security & Secret Hygiene**:
  - **Fail-Fast Validation**: Validates the presence of `STRIPE_SECRET_KEY` before test execution starts.
  - **Live-Key Production Guard**: Automatically rejects live API keys (`sk_live_...`, `rk_live_...`) to protect production Stripe accounts from automated mutation or deletion.
  - **Header Masking**: Automatically blacklists `Authorization` headers in REST Assured logs, preventing test tokens from leaking into build logs and reports.
  - **RFC 2606 Compliant Test Data**: Uses reserved domains (`@example.com`) to prevent unsolicited email delivery.

- **Resource Lifecycle & Isolation (`BaseApiTest`)**:
  - Helper methods for entity creation (`setupTestCustomer`, `setupTestProduct`).
  - Guaranteed resource cleanup via `@AfterEach` / `@AfterAll` teardown routines with ID nullification to prevent double deletions.

- **Advanced API Testing Capabilities**:
  - **Cursor-Based Pagination**: Resilient multi-page retrieval using `has_more` and `starting_after` with a defensive maximum page cap.
  - **Idempotency Guarantees**: Verifies `Idempotency-Key` duplicate suppression, `Idempotent-Replayed: true` headers, and validation errors upon payload mutation.
  - **Both Independent and Flow-Based Testing**: Showcases isolated tests as well as ordered stateful flows via `@TestMethodOrder(OrderAnnotation.class)`.

- **Resilience Testing & Mock Fault Injection (`WireMock` + Circuit Breaker)**:
  - Injects chaos scenarios without external risk or cost: downstream 5xx errors, 5000ms latency delays, and Stripe HTTP 429 rate limit contracts (`rate_limit_error`).
  - Verifies Circuit Breaker tripping, fail-fast rejection under 100ms, and state recovery upon healthy downstream responses.

---

## Project Structure

```text
stripe-api-tests-rest-assured/
├── .github/
│   └── workflows/
│       └── maven.yml                     # GitHub Actions CI workflow
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── automation/
│   │               └── stripe/
│   │                   └── middleware/
│   │                       └── ResilientStripeClient.java  # Resilient middleware simulation (Circuit Breaker)
│   └── test/
│       └── java/
│           └── com/
│               └── automation/
│                   └── stripe/
│                       ├── base/
│                       │   ├── BaseApiTest.java          # Base test class with setup/teardown helpers
│                       │   └── StripeSpecFactory.java    # Request/response specifications & security
│                       ├── customers/
│                       │   ├── CustomerAuthErrorValidationTest.java  # Negative 401 auth tests
│                       │   ├── CustomerCrudFlowTest.java            # Sequential ordered CRUD journey
│                       │   ├── CustomerCrudIndependentTest.java     # Isolated CRUD operations
│                       │   ├── CustomerEdgeCasesTest.java           # 404, empty params, cursor pagination
│                       │   └── IdempotencyTest.java                 # Idempotency key replay & mutation
│                       ├── mocking/
│                       │   └── MockingTest.java          # WireMock fault injection & circuit breaker tests
│                       └── products/
│                           └── ProductCrudIndependentTest.java      # Product CRUD, limits, 400 & 404 validation
├── pom.xml                               # Maven project configuration & Allure plugins
└── README.md                             # Documentation
```

---

## Prerequisites

Before running the test suite, ensure the following tools are installed:

- **Java Development Kit (JDK)**: Version 17 or higher (`java -version`)
- **Apache Maven**: Version 3.8 or higher (`mvn -version`)
- **Stripe Account**: A test account with access to test API keys from the [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys)

---

## Environment Configuration

The framework retrieves your Stripe test secret key from the `STRIPE_SECRET_KEY` environment variable.

> [!IMPORTANT]
> Always use your **Test mode** API key (starts with `sk_test_...` or `rk_test_...`). Live keys (`sk_live_...`) are blocked by design.

### Setting the Environment Variable

#### macOS / Linux
```bash
export STRIPE_SECRET_KEY="sk_test_yourSecretKeyHere"
```

To persist this variable, append the command above to your `~/.zshrc` or `~/.bashrc`.

#### Windows (PowerShell)
```powershell
$env:STRIPE_SECRET_KEY="sk_test_yourSecretKeyHere"
```

#### Windows (Command Prompt)
```cmd
set STRIPE_SECRET_KEY=sk_test_yourSecretKeyHere
```

#### IntelliJ IDEA / Eclipse
1. Open **Run/Debug Configurations**.
2. Select the JUnit configuration or template.
3. In the **Environment Variables** field, add:
   ```text
   STRIPE_SECRET_KEY=sk_test_yourSecretKeyHere
   ```

---

## Running Tests

All tests are managed via Maven and Surefire plugin.

### Run the entire test suite
```bash
mvn clean test
```

### Run a specific test class
```bash
mvn test -Dtest=CustomerCrudIndependentTest
```

### Run a specific test method
```bash
mvn test -Dtest=CustomerCrudIndependentTest#createCustomer_ShouldReturnValidId
```

### Run all tests in a package
```bash
# Run all customer tests
mvn test -Dtest="com.automation.stripe.customers.*"

# Run all product tests
mvn test -Dtest="com.automation.stripe.products.*"
```

---

## Allure Test Reporting

The framework integrates `allure-junit5` and `aspectjweaver` to generate rich, interactive HTML reports with detailed timelines, request/response metrics, and step-level execution details.

### 1. Generate Allure Report
After running tests, generate the HTML report:
```bash
mvn allure:report
```
The report is generated under `target/site/allure-maven-plugin/`.

### 2. View Allure Report Locally
To automatically launch the report in your default web browser:
```bash
mvn allure:serve
```

### 3. View Live Report on GitHub (GitHub Pages)
Every commit to the `main` branch automatically generates and publishes the interactive Allure report to GitHub Pages:

👉 **[https://tetianadoc.github.io/stripe-api-tests-rest-assured/](https://tetianadoc.github.io/stripe-api-tests-rest-assured/)**

No local setup or download needed — view full interactive charts, test suites, timelines, and behaviors directly in your browser.

---

## Test Coverage & Scenarios

### Customer API (`/v1/customers`)

| Test Class | Method | HTTP Method | Endpoint | Scenario Verified | Expected Status |
|---|---|---|---|---|---|
| `CustomerCrudIndependentTest` | `createCustomer_ShouldReturnValidId` | `POST` | `/customers` | Creates customer and validates `cus_` ID format | `200 OK` |
| `CustomerCrudIndependentTest` | `retrieveCustomer_ShouldReturnValidData` | `GET` | `/customers/{id}` | Retrieves customer details and validates email match | `200 OK` |
| `CustomerCrudIndependentTest` | `updateCustomer_ShouldModifyData` | `POST` | `/customers/{id}` | Updates existing customer fields (`name`, `email`) | `200 OK` |
| `CustomerCrudIndependentTest` | `deleteCustomer_ShouldReturnTrue` | `DELETE` | `/customers/{id}` | Deletes customer and verifies `deleted: true` response | `200 OK` |
| `CustomerCrudFlowTest` | `createCustomer` $\rightarrow$ `deleteCustomer` | Mixed | `/customers` | Ordered E2E lifecycle (Create $\rightarrow$ Read $\rightarrow$ Update $\rightarrow$ Delete) | `200 OK` |
| `CustomerAuthErrorValidationTest` | `getCustomers_WithInvalidAuth_ShouldReturn401` | `GET` | `/customers` | Unauthorized access with invalid API key | `401 Unauthorized` |
| `CustomerEdgeCasesTest` | `retrieveCustomerWithInvalidId_ShouldReturn404` | `GET` | `/customers/{id}` | Requesting non-existent customer ID | `404 Not Found` |
| `CustomerEdgeCasesTest` | `listCustomersWithLimitQueryParameter_ShouldReturnCorrectSize` | `GET` | `/customers` | Validates `limit` query param restriction | `200 OK` |
| `CustomerEdgeCasesTest` | `createCustomerWithEmptyEmail_ShouldReturn200` | `POST` | `/customers` | Empty email string is persisted as `null` | `200 OK` |
| `CustomerEdgeCasesTest` | `calculateTotalNumberOfCreatedCustomersWithPagination` | `GET` | `/customers` | Multi-page cursor pagination loop (`has_more`, `starting_after`) | `200 OK` |
| `IdempotencyTest` | `createCustomerTwiceWithSameIdempotencyKey_ShouldNotCreateDuplicates` | `POST` | `/customers` | Replaying identical request returns `Idempotent-Replayed: true` | `200 OK` |
| `IdempotencyTest` | `createCustomerWithSameIdempotencyKeyButDifferentPayload_ShouldReturn400` | `POST` | `/customers` | Reusing key with altered payload triggers `idempotency_error` | `400 Bad Request` |

### Products API (`/v1/products`)

| Test Class | Method | HTTP Method | Endpoint | Scenario Verified | Expected Status |
|---|---|---|---|---|---|
| `ProductCrudIndependentTest` | `createProduct_ShouldReturnValidId` | `POST` | `/products` | Creates product, asserts `prod_` ID and name | `200 OK` |
| `ProductCrudIndependentTest` | `createProductWithoutName_ShouldReturn400` | `POST` | `/products` | Missing mandatory `name` parameter validation | `400 Bad Request` |
| `ProductCrudIndependentTest` | `retrieveProduct_ShouldReturnCorrectData` | `GET` | `/products/{id}` | Retrieves created product by ID | `200 OK` |
| `ProductCrudIndependentTest` | `retrieveProductWithInvalidId_ShouldReturn404` | `GET` | `/products/{id}` | Requesting non-existent product ID returns error message | `404 Not Found` |
| `ProductCrudIndependentTest` | `updateProduct_ShouldModifyExistingData` | `POST` | `/products/{id}` | Updates product name and archives product (`active: false`) | `200 OK` |
| `ProductCrudIndependentTest` | `deleteProduct_ShouldReturnDeletedTrue` | `DELETE` | `/products/{id}` | Deletes product and verifies `deleted: true` | `200 OK` |
| `ProductCrudIndependentTest` | `listProductsWithLimit_ShouldReturnValidList` | `GET` | `/products` | Lists products with `limit` query parameter | `200 OK` |

### Mocking & Resilience Scenarios (WireMock + Circuit Breaker)

| Test Class | Method | Simulated Endpoint | Scenario Verified | Expected Behavior |
|---|---|---|---|---|
| `MockingTest` | `simulateServiceDown_ShouldReturn503AndHideStackTrace` | `POST /v1/customers` | Simulates downstream gateway failure (`503`) | HTTP 503, `ContentType.JSON`, sanitized message, stack trace hidden |
| `MockingTest` | `simulateNetworkLatency_ShouldDelayResponse` | `GET /v1/products` | Simulates slow network latency (`5000ms` fixed delay) | HTTP 200, response time $\ge 5000\text{ms}$, success spec validated |
| `MockingTest` | `circuitBreaker_ShouldTripAndFailFastOnRepeatedHttp500Errors` | `GET /v1/products` | Simulates repeated 500 errors tripping Circuit Breaker threshold (3) | 4th call fails fast in $< 100\text{ms}$ with `Circuit Breaker is OPEN` |
| `MockingTest` | `simulateRateLimiting_ShouldReturn429WithRateLimitError` | `POST /v1/customers` | Simulates Stripe 429 rate limit with `Retry-After: 1` header | HTTP 429, `Retry-After: 1`, `error.type: "rate_limit_error"` |
| `MockingTest` | `circuitBreaker_ShouldResetConsecutiveFailuresOnSuccessfulResponse` | `GET /v1/products` | Simulates transient errors (2 $\times$ 500) followed by 200 recovery | Counter resets to 0; subsequent single failure does not trip breaker |
| `MockingTest` | `circuitBreaker_ShouldTripAndFailFastAfterRepeatedTimeouts` | `GET /v1/products` | Simulates consecutive latency timeouts ($3 \times 5000\text{ms}$) | 4th request trips OPEN and fails fast instantly ($< 100\text{ms}$) |

---

## Design Patterns & Best Practices

### 1. Specification Factory Pattern
Instead of rebuilding request configs inside every test method, `StripeSpecFactory` centralizes:
- Base URI: `https://api.stripe.com/v1`
- Content-Type: `ContentType.URLENC` (`application/x-www-form-urlencoded`)
- OAuth 2.0 Bearer authentication
- Log redaction rules

```java
// Centralized request execution
given()
    .spec(StripeSpecFactory.getBaseRequestSpec())
    .formParam("name", "Jane Doe")
    .when()
    .post("/customers")
    .then()
    .spec(StripeSpecFactory.getSuccessResponseSpec());
```

### 2. Live Key Protection & Credential Blacklisting
Security guards prevent accidental data destruction and secret exposure:
```java
// Rejection of live keys
if (key.startsWith("sk_live_") || key.startsWith("rk_live_")) {
    throw new IllegalStateException("Live Stripe keys (sk_live_/rk_live_) are not permitted in automated test runs");
}

// Authorization header masking in logs
LogConfig.logConfig().blacklistHeader("Authorization");
```

### 3. Safe Teardown & Idempotent Cleanup
All created test resources are tracked and deleted in `@AfterEach` (or `@AfterAll`) hooks:
- Identifiers are set to `null` immediately after deletion to avoid duplicate delete attempts.
- In independent tests, IDs are safely extracted into instance fields before asserting subsequent assertions, ensuring cleanup executes even if a check fails.

### 4. Cursor-Based Pagination
Stripe utilizes cursor pagination rather than page numbers. The suite demonstrates resilient cursor navigation:
- Evaluates `has_more` boolean flag.
- Extracts the ID of the last element in the current page and passes it as `starting_after`.
- Implements a defensive `maxPages` guard to eliminate potential infinite loops in CI.

### 5. Mock Fault Injection & Resilience Testing: White-Box vs. Black-Box SDET Architecture

In distributed microservice architectures (e.g., e-commerce platforms, payment gateways, or gaming platforms like **LeoVegas**), services never communicate directly with external payment providers from client applications. Instead, an internal backend API Gateway / Payment Service serves as an intermediary.

When a third-party dependency like Stripe experiences high latency, 5xx server outages, or rate-limiting thresholds (HTTP 429), the backend must not hang worker threads or trigger cascading failures across the platform. It must implement **resilience patterns (Circuit Breaker, timeout boundaries, and instant fail-fast logic)**.

#### The Testing Challenge: Why WireMock?
Testing failure boundaries directly against live external third-party APIs is impractical:
- You cannot force Stripe's live or sandbox servers to return `500 Internal Server Error` or inject arbitrary 5-second network latency on demand.
- Deliberately triggering `429 Too Many Requests` risks IP rate-limiting bans or account throttling in shared CI environments.
- Using **WireMock**, the framework virtualizes the external provider locally with dynamic port allocation, enabling deterministic chaos testing.

#### The Middleware Simulation (`ResilientStripeClient`)
`ResilientStripeClient` provides an in-process simulation of how an internal backend microservice protects itself:
- **`CLOSED`**: Requests pass through to the downstream endpoint.
- **Consecutive Failures**: Any response exceeding `TIMEOUT_MS` (2000ms) or returning HTTP $\ge 500$ increments an internal failure counter.
- **`OPEN` (Tripped)**: Reaching the `FAILURE_THRESHOLD` (3 failures) trips the circuit. Subsequent calls are rejected **instantly** ($< 100\text{ms}$) without dispatching a network call.
- **Recovery**: A single successful `200 OK` resets the failure counter to 0.

---

#### 🌟 Architectural Transition: Developer White-Box vs. SDET Black-Box Testing

Understanding this transition is essential for designing enterprise API automation frameworks:

##### 1. Developer White-Box / Unit Testing (In-Process)
In a developer unit test, the test resides inside the same JVM as the client code. It directly instantiates the Java client class and asserts Java language exceptions:

```java
// White-Box / Developer Style: tightly coupled to internal Java classes and exceptions
@Test
void circuitBreaker_ShouldTripAndFailFastOnRepeatedHttp500Errors() {
    stubFor(get(urlEqualTo("/v1/products"))
            .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", ContentType.JSON.toString())
                    .withBody("{\"error\": {\"message\": \"Internal Server Error\"}}")));

    // White-box: directly instantiating the Java middleware class
    ResilientStripeClient client = new ResilientStripeClient();

    // 3 consecutive 500 errors reach FAILURE_THRESHOLD
    for (int i = 0; i < 3; i++) {
        Response response = client.getProducts(mockBaseUrl);
        assertEquals(500, response.statusCode());
    }

    // 4th request must fail fast with circuit breaker OPEN
    long startTime = System.currentTimeMillis();
    IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.getProducts(mockBaseUrl)
    );
    long executionTime = System.currentTimeMillis() - startTime;

    assertTrue(executionTime < 100, 
            "Expected instant failure under 100ms, but took " + executionTime + "ms");
    assertTrue(exception.getMessage().contains("Circuit Breaker is OPEN"));
}
```

##### 2. SDET Black-Box Integration Testing (True API Automation)
In a professional SDET framework testing an end-to-end environment, **you completely eliminate the `ResilientStripeClient` instantiation and all Java exception assertions**. 

Instead of calling internal Java methods, the SDET test:
1. Configures **WireMock** to simulate the third-party dependency (e.g. fake Stripe returning 500s or 5-second delays).
2. Uses **REST Assured** to send real HTTP requests to the actual microservice under test (e.g. `http://api.leovegas.local/api/v1/payments/products`).
3. The internal service calls WireMock, fails, and increments its own internal circuit breaker state.
4. On the 4th request, REST Assured fires another HTTP request and verifies:
   - The microservice immediately returns HTTP `503 Service Unavailable` (or `502 Bad Gateway`).
   - The response payload matches the contract (`"Payment service temporarily unavailable"`).
   - Execution time is verified under 100ms, proving the backend failed fast over HTTP without hanging on downstream timeouts.

```java
// SDET Black-Box Style: 100% REST Assured HTTP verification, Zero internal Java coupling
@Test
void circuitBreaker_ShouldTripAndFailFastOnRepeatedHttp500Errors() {
    String leovegasApiUrl = "http://api.leovegas.local"; // The real microservice under test

    // 1. Tell WireMock (the virtualized downstream Stripe) to return 500s
    stubFor(get(urlEqualTo("/v1/products"))
            .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"Stripe Internal Error\"}")));

    // 2. Hit the LeoVegas API 3 times to trip its internal Circuit Breaker
    for (int i = 0; i < 3; i++) {
        given()
            .baseUri(leovegasApiUrl)
        .when()
            .get("/api/v1/payments/products")
        .then()
            // LeoVegas gracefully handles the Stripe 500 and returns a 502/503
            .statusCode(anyOf(is(502), is(503)));
    }

    // 3. The 4th request must fail fast over the wire (Circuit Breaker is OPEN)
    long startTime = System.currentTimeMillis();

    given()
        .baseUri(leovegasApiUrl)
    .when()
        .get("/api/v1/payments/products")
    .then()
        .statusCode(503) // LeoVegas rejects immediately without calling WireMock
        .body("message", equalTo("Payment service temporarily unavailable"));

    long executionTime = System.currentTimeMillis() - startTime;

    // 4. Prove it failed instantly over HTTP without waiting for network timeouts
    assertTrue(executionTime < 100, 
            "Expected instant failure under 100ms, but took " + executionTime + "ms");
}
```

> [!TIP]
> **Why this matters for Staff/Senior SDET Interviews:**  
> A developer unit test validates *in-memory Java logic* and catches language exceptions (`IllegalStateException`). An SDET validates *system behavior over the wire* — verifying HTTP status codes, JSON payload contracts, and HTTP latency metrics. The test suite operates completely decoupled from internal implementation choices, language stacks, or framework internals.

---

## Continuous Integration (CI/CD)

The project includes an automated GitHub Actions workflow defined in [`.github/workflows/maven.yml`](.github/workflows/maven.yml):

- **Triggers**: Executed on every `push` and `pull_request` targeting `main` or `master`.
- **Environment**: Runs on `ubuntu-latest` with Eclipse Temurin JDK 21 and Maven caching.
- **Secret Injection**: Injects `STRIPE_SECRET_KEY` securely from GitHub Repository Secrets.
- **Reporting**: Generates the Allure HTML report and uploads it as an artifact (`allure-html-report`) retained in the CI run summary.

### Setting up GitHub Secrets
To enable the CI pipeline in your repository:
1. Navigate to **Settings** > **Secrets and variables** > **Actions** in your GitHub repository.
2. Click **New repository secret**.
3. Name: `STRIPE_SECRET_KEY`.
4. Value: Your Stripe test API key (`sk_test_...`).
5. Click **Add secret**.

---

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details or use freely for testing and educational purposes.
