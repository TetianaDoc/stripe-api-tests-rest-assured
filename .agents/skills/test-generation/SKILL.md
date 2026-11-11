---
name: test-generation
description: Generates idiomatic REST Assured 5.4 + JUnit 5 + Hamcrest + Allure API tests for this Stripe test automation project. Produces compilable, isolated test methods extending BaseApiTest, reusing StripeSpecFactory, using safe @example.com test data, and ensuring deterministic @AfterEach resource cleanup. Use when fixing Coverage findings or adding new Stripe API tests.
---

# API Test Generation Skill — Stripe API Tests

Use this skill to write new `@Test` methods or test classes for Stripe API endpoints (`/customers`, `/products`, etc.), especially when resolving `Coverage` findings during the `/review` loop.

## Conventions

- **Framework:** JUnit 5 (`org.junit.jupiter.api.Test`, `@AfterEach`, `@DisplayName`)
- **HTTP Client:** REST Assured 5.4 (`given().spec(StripeSpecFactory.getBaseRequestSpec())...`)
- **Base Class:** Extend `BaseApiTest` whenever tests create or delete Stripe customers (`customerId`) or products (`productId`)
- **Specifications:**
  - Request: `StripeSpecFactory.getBaseRequestSpec()`
  - 200 OK Response: `StripeSpecFactory.getSuccessResponseSpec()`
  - Path params: `.pathParam("id", id)` with `"/customers/{id}"` or `"/products/{id}"`
- **Matchers:** Hamcrest (`equalTo`, `notNullValue`, `startsWith`, `containsString`, `hasSize`, `lessThanOrEqualTo`, `nullValue`)
- **Test Data Hygiene:**
  - Always use RFC 2606 `@example.com` email addresses (e.g. `"test.user@example.com"`)
  - Store any created resource ID in `this.customerId` or `this.productId` immediately so `@AfterEach` (`deleteTestCustomer()` / `deleteTestProduct()`) cleans up even if a subsequent assertion fails
  - Negative tests (`400`, `401`, `404`) that do not need an existing resource must **not** call `setupTestCustomer()` or `setupTestProduct()`

## Templates

### 1. Independent CRUD / Positive Test (with guaranteed cleanup)

```java
@Test
void createCustomerWithMetadata_ShouldPersistMetadata() {
    customerId = given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .formParam("name", "Metadata Test User")
            .formParam("email", "metadata.user@example.com")
            .formParam("metadata[tier]", "enterprise")
            .when()
            .post("/customers")
            .then()
            .spec(StripeSpecFactory.getSuccessResponseSpec())
            .extract().path("id");

    given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .pathParam("customerId", customerId)
            .when()
            .get("/customers/{customerId}")
            .then()
            .spec(StripeSpecFactory.getSuccessResponseSpec())
            .body("id", equalTo(customerId))
            .body("metadata.tier", equalTo("enterprise"));
}
```

### 2. Negative Validation Test (`400 Bad Request` — no setup required)

```java
@Test
void createProductWithoutRequiredName_ShouldReturn400() {
    given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .formParam("description", "Missing name parameter")
            .when()
            .post("/products")
            .then()
            .statusCode(400)
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.param", equalTo("name"));
}
```

### 3. Resource Not Found Test (`404 Not Found` — no setup required)

```java
@Test
void retrieveNonExistentProduct_ShouldReturn404() {
    given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .pathParam("productId", "prod_nonexistent12345")
            .when()
            .get("/products/{productId}")
            .then()
            .statusCode(404)
            .body("error.type", equalTo("invalid_request_error"))
            .body("error.message", containsString("No such product"));
}
```

## After Writing Tests

Run the Maven verification chain:

```bash
# 1. Always verify compilation first:
mvn test-compile

# 2. If STRIPE_SECRET_KEY is configured in the environment, run the modified test class:
if [ -n "${STRIPE_SECRET_KEY:-}" ]; then
  mvn test -Dtest=<TestClassName>
fi
```

## Scope Discipline

- Generate only the test methods required by the finding or user request.
- Reuse `BaseApiTest` helpers (`setupTestCustomer`, `setupTestProduct`, `deleteTestCustomer`, `deleteTestProduct`) and `StripeSpecFactory` specifications rather than duplicating boilerplate.
- Never hardcode real API keys or real corporate email addresses.
