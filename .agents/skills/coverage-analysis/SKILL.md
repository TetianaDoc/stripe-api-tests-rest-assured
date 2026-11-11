---
name: coverage-analysis
description: API test scenario and endpoint coverage gap analysis for this Stripe REST Assured + JUnit 5 test suite. Maps existing test classes against Stripe API resources (/customers, /products), HTTP methods (POST, GET, DELETE), status codes (200, 400, 401, 404), negative/validation paths, idempotency, and pagination edge cases. Use during review to surface missing API test scenarios; pair with test-generation to implement them.
---

# API Test Coverage Analysis Skill — Stripe API Tests

In an API test automation repository (`src/test/java/com/automation/stripe/...`), coverage analysis evaluates **API contract & scenario completeness** rather than production-code line coverage. Apply this skill to identify high-value missing negative, boundary, and contract test cases for the target test class or package.

## Scenario Coverage Matrix per Stripe Resource

| Scenario Category | Expected HTTP Status | Priority |
|---|---|---|
| **CRUD Happy Paths** (`POST` create, `GET /{id}` retrieve, `POST /{id}` update, `DELETE /{id}` delete, `GET` list) | `200 OK` | High — baseline resource lifecycle |
| **Authentication Failure** (missing token or invalid Bearer token) | `401 Unauthorized` (`invalid_request_error`) | High — security contract |
| **Resource Not Found** (`GET`, `POST` update, or `DELETE` with non-existent ID `cus_nonexistent` / `prod_nonexistent`) | `404 Not Found` (`invalid_request_error` + `"No such ..."`) | High — negative path completeness |
| **Validation / Missing Required Fields** (e.g. `POST /products` without required `name`, invalid email/param format, unknown parameter) | `400 Bad Request` (`invalid_request_error`) | High — input validation contract |
| **Idempotency** (`Idempotency-Key` safe replay returning `Idempotent-Replayed: true` + `400 idempotency_error` on mutated payload) | `200 OK` / `400 Bad Request` | Medium — reliability contract |
| **Pagination & Filtering** (`limit` bounds `1..100`, `limit=0` or `limit=101` -> `400`, `starting_after` cursor, `active` filter on products) | `200 OK` / `400 Bad Request` | Medium — collection contract |
| **Metadata & Special Fields** (`metadata[key]=value` creation/update/clearing, boolean flags like `active=false`, empty string clearing) | `200 OK` | Low — field-level edge cases |

## Procedure

### 1. Determine Target Resource & Existing Tests
- Read the target file (or package).
- If `target` is `BaseApiTest.java`, `StripeSpecFactory.java`, `pom.xml`, or a non-test helper class, write `_N/A — scenario coverage analysis applies only to API test classes/packages._` to `output_path` and return `COUNT=0`.
- Identify the Stripe resource under test (e.g. `/customers` in `com.automation.stripe.customers`, `/products` in `com.automation.stripe.products`).
- Scan sibling test classes in the **same resource package** (using `Glob` / `Grep` within that package only) so you do not flag a scenario as "missing" in `CustomerCrudIndependentTest` if it is already covered in `CustomerEdgeCasesTest`, `CustomerAuthErrorValidationTest`, or `IdempotencyTest`.

### 2. Identify High-Value Missing Scenarios in the Target Class
Depending on the purpose of the target test class, check for missing scenarios that belong in that class:
- **In `*CrudIndependentTest`:**
  - Does create assert key response fields (`id` prefix `cus_`/`prod_`, `object` type `"customer"`/`"product"`, submitted fields)?
  - Does retrieve (`GET /{id}`) assert `id`, `object`, and created fields?
  - Does update (`POST /{id}`) verify both updated fields and unchanged fields (or metadata)?
  - Does delete (`DELETE /{id}`) verify `deleted: true`, `id: <expected>`, and optionally that a subsequent `GET /{id}` returns `deleted: true` (or `404`)?
- **In `*EdgeCasesTest` / negative test classes:**
  - Missing required field (`400 Bad Request`, e.g. `POST /products` with empty/missing `name`)
  - Invalid parameter type or out-of-range pagination `limit` (e.g. `limit=0` or `limit=101` returning `400 Bad Request`)
  - Updating or deleting a non-existent resource ID (`POST /customers/cus_invalidID` or `DELETE /customers/cus_invalidID` returning `404 Not Found`)
  - Unknown field rejection (`POST` with an unsupported form param returning `400 parameter_unknown`)
- **In `products/` package specifically:**
  - Notice `products/` currently has only `ProductCrudIndependentTest.java` (no negative 400/404 tests, no missing-`name` validation test, no list/pagination test). Surface the highest-value missing product scenarios (up to 2–3 concrete `@Test` methods per review pass so the suite stays focused).

### 3. Cap and Prioritize Findings
- Emit **at most 3 coverage findings per file** so the implementator can add clean, high-value tests without bloating a single class.
- Prioritize:
  1. `High` — Missing negative validation (400 missing required field / invalid param) or 404 on non-existent resource for the resource under review
  2. `Medium` — Missing assertion on core contract fields or missing boundary test (e.g. pagination limit validation, metadata update, post-delete verification)
  3. `Low` — Secondary edge case

## Output Contract

When invoked with `feedback_path`, write findings using the shared format:

```markdown
### [ ] F<N> · <Severity> · Coverage
**Location:** `src/test/java/com/automation/stripe/products/ProductCrudIndependentTest.java:83`
**Issue:** `ProductCrudIndependentTest` covers only `200 OK` CRUD happy paths; there is no negative test verifying that `POST /products` without the required `name` parameter returns `400 Bad Request` (`invalid_request_error`) or that retrieving a non-existent product ID returns `404 Not Found`.
**Fix:**
```java
@Test
void createProductWithoutName_ShouldReturn400() {
    given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .formParam("description", "Product missing required name")
            .when()
            .post("/products")
            .then()
            .statusCode(400)
            .body("error.type", equalTo("invalid_request_error"));
}
```
**Apply with:** the `test-generation` skill.
```

`<Category>` is always `Coverage`.

## Scope Discipline

- Do **not** flag code quality or refactoring issues in existing tests — that belongs to `code-review` and `restassured-review`.
- Do **not** propose tests that require external webhooks, browser flows, or live payment card processing unless already present in the target.
- Every proposed `@Test` must be self-contained, clean up any created Stripe resource via `customerId` / `productId` + `@AfterEach`, and run deterministically against the Stripe test API.
