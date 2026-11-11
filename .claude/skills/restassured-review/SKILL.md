---
name: restassured-review
description: Domain review for REST Assured 5.4 API test automation idioms, RequestSpecification and ResponseSpecification reuse, path/query/form parameter handling, Hamcrest matchers, Jackson POJO serialization, pagination, idempotency, and safe resource cleanup. Use when reviewing API test classes, base test classes, or specification factories.
---

# REST Assured Review Skill — Stripe API Tests

Apply this checklist to the target code. Focus strictly on idiomatic **REST Assured 5.4** usage, HTTP contract verification, specification design, and reliable API test execution against Stripe's `/v1` REST API.

## Checklist

### 1. Request & Response Specification Architecture (`StripeSpecFactory`)
- [ ] Every test request uses `StripeSpecFactory.getBaseRequestSpec()` (or a dedicated spec factory method) — never raw `given().baseUri(...)` duplicating URI/auth/content-type
- [ ] Every `200 OK` assertion chains `.spec(StripeSpecFactory.getSuccessResponseSpec())` rather than repeating `.statusCode(200)` manually
- [ ] `ContentType.URLENC` and `ContentType.JSON` enums (`io.restassured.http.ContentType`) used instead of raw `"application/x-www-form-urlencoded"` or `"application/json"` strings
- [ ] `getSuccessResponseSpec()` validates `.expectContentType(ContentType.JSON)` in addition to `.expectStatusCode(200)`
- [ ] Reusable error response specs (or helper assertion methods) used for standard Stripe error envelopes (`error.type`, `error.message`, HTTP 400/401/404) when repeated across tests
- [ ] `AllureRestAssured` filter wired into `RequestSpecBuilder` when Allure reporting is active in `pom.xml`

### 2. URL Path, Query & Form Parameters
- [ ] Path variables use named placeholders `.pathParam("id", customerId)` with `"/customers/{id}"` instead of string concatenation `"/customers/" + customerId` (prevents URL encoding bugs and improves request log readability)
- [ ] Form parameters for Stripe `application/x-www-form-urlencoded` requests use `.formParam(...)` or `.formParams(Map)` cleanly; nested Stripe parameters (e.g. `metadata[env]`) are formatted consistently
- [ ] Query parameters on `GET` endpoints use `.queryParam(...)` — never appended manually to the URL string (`"/customers?limit=3"`)

### 3. Given / When / Then Fluency & Safe ID Extraction
- [ ] Strict `given() ... .when() ... .then() ...` structure maintained; avoid unnecessary `import static io.restassured.RestAssured.when;` if `given()` starts every chain
- [ ] **Teardown-safe ID extraction:** When a test creates a Stripe resource (`POST /customers` or `POST /products`) and also asserts multiple body fields, extract the response or capture `customerId` / `productId` before secondary body assertions can fail, OR create via `setupTestCustomer(...)` and assert on the response so that a failed field assertion does not leave `customerId == null` and skip `@AfterEach` cleanup
- [ ] Avoid redundant `.extract().path("id")` when the return value is discarded

### 4. Response Validation & Hamcrest Matchers
- [ ] Error responses assert **both** HTTP status code (`400`, `401`, `404`) AND Stripe error payload structure (`error.type`, and `error.message` or `error.code` where deterministic)
- [ ] Multiple `.body(...)` assertions on the same response are chained fluently on `.then()`
- [ ] Proper Hamcrest matchers used for types: `equalTo(true)` / `equalTo(false)` for booleans (never `"true"` string), `notNullValue()`, `startsWith("cus_"` / `"prod_")` for Stripe prefixed IDs, `hasSize(lessThanOrEqualTo(limit))` for lists
- [ ] Pagination loops (`has_more` + `starting_after`) include:
  - A defensive max-iterations / max-pages guard so a malformed cursor cannot cause an infinite loop in CI
  - Safe null/boolean handling on `response.path("has_more")` (`Boolean.TRUE.equals(...)`)

### 5. Idempotency & Headers
- [ ] Idempotency replay tests verify `.header("Idempotent-Replayed", "true")` and matching resource `id`
- [ ] Auth-override tests (e.g. invalid API key) cleanly override auth on the request spec or use an unauthenticated spec factory helper (`StripeSpecFactory.getUnauthenticatedRequestSpec()` / `getRequestSpecWithKey(String key)`) rather than relying on internal RestAssured auth-override order

## Output Contract

When invoked by an agent with a `feedback_path`, write findings to that file using the shared format:

```markdown
# REST Assured Review — Iteration <N>

**Target:** <path or scope>
**Date:** <YYYY-MM-DD>

## Summary

- Total findings: <N>
- Critical: <X> | High: <Y> | Medium: <Z> | Low: <W>

## Findings

### [ ] F1 · Medium · RestAssured
**Location:** `src/test/java/com/automation/stripe/customers/CustomerCrudIndependentTest.java:43`
**Issue:** Endpoint path uses string concatenation `"/customers/" + customerId` instead of REST Assured path parameters, bypassing URL encoding and template logging.
**Fix:**
```java
given()
        .spec(StripeSpecFactory.getBaseRequestSpec())
        .pathParam("customerId", customerId)
        .when()
        .get("/customers/{customerId}")
        .then()
        .spec(StripeSpecFactory.getSuccessResponseSpec())
        .body("email", equalTo("indretruser@example.com"));
```
```

`<Category>` is always `RestAssured`.

Severity scale:
- `Critical` — Broken HTTP contract or resource leak caused by failed extraction before teardown
- `High` — Missing status/error-body validation, infinite pagination loop risk, broken spec state sharing
- `Medium` — String concatenation in URLs instead of `pathParam`, raw content-type strings instead of `ContentType`, weak ID matcher (`notNullValue()` instead of `startsWith("cus_")`)
- `Low` — Minor DSL fluency or matcher readability improvement

## Scope Discipline

- Own REST Assured DSL, `RequestSpecification`/`ResponseSpecification` usage, HTTP parameter/header handling, matchers, and extraction patterns.
- Defer general Java/JUnit 5 naming & lifecycle to `code-review`, secret exposure/log blacklisting to `security-audit`, and missing test methods to `coverage-analysis`.
