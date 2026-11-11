---
name: code-review
description: Comprehensive Java 17 and JUnit 5 test architecture review for this Maven + REST Assured + Jackson + Allure API test automation codebase. Checks Java 17 idioms, JUnit 5 lifecycle and isolation, shared mutable state hazards, Allure reporting conventions, and test code antipatterns. Use when reviewing a test file, base class, or package for code quality, reliability, and maintainability.
---

# Code Review Skill — Stripe API Tests (Java 17 + JUnit 5 + Maven)

Apply this review checklist to the target code (file, package, or diff). Be exhaustive yet precise — surface real defects, flakiness risks, and maintainability issues rather than stylistic noise. The codebase is Java 17 + Maven + JUnit Jupiter 5.10 + REST Assured 5.4 + Jackson 2.16 + Allure 2.25.

## Checklist

### Java 17 & Language Idioms
- [ ] Local variable type inference (`var`) used only where the right-hand type is obvious; explicit types used when extracting untyped JSON paths (e.g. `List<String> customerIds = response.path("data.id")`)
- [ ] Immutable Java `record` classes preferred for request/response POJOs and test data fixtures when Jackson serialization/deserialization is used
- [ ] No fully-qualified inline class names in method bodies (e.g. `java.util.List`, `org.junit.jupiter.api.Assertions`, `io.restassured.RestAssured`) — use clean top-level imports
- [ ] No unused imports or redundant wildcard imports when only 1–2 symbols are needed
- [ ] No raw types (`List`, `Map` without generics)
- [ ] Standard collection factories (`List.of(...)`, `Map.of(...)`) preferred over mutable builders for constant test data
- [ ] Method and variable names free of typos (e.g. `retrieveCustomer` not `retriveCustomer`, `shouldModifyData` not `ShouldMofifyData`) and follow a consistent naming convention (`action_givenCondition_shouldExpectedOutcome` or `action_ShouldExpectedBehavior`)
- [ ] No noisy `System.out.println(...)` debugging statements in tests or `BaseApiTest` — use Allure steps (`Allure.step(...)` / `@Step`), SLF4J logger, or REST Assured logging filters

### JUnit 5 Lifecycle & Test Isolation
- [ ] JUnit 5 test classes and `@Test` / `@BeforeEach` / `@AfterEach` methods are package-private (no unnecessary `public` modifiers)
- [ ] Independent test classes must NOT depend on execution order; each `@Test` arranges its own data (`setupTestCustomer` / `setupTestProduct`) and cleans up in `@AfterEach`
- [ ] Ordered flow test classes (`@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`) must include an `@AfterAll` safety net calling `deleteTestCustomer()` / `deleteTestProduct()` so failed mid-flow steps do not leak resources in Stripe
- [ ] Tests that do not require server-side state (e.g. `401 Unauthorized` with an invalid token, `404 Not Found` with a synthetic ID) must NOT call `setupTestCustomer()` / `setupTestProduct()` unnecessarily
- [ ] Shared mutable fields in `BaseApiTest` (`customerId`, `productId`) are reset to `null` in teardown and never leaked across tests; if a single test creates multiple resources of the same type, all created IDs must be tracked and cleaned up

### Antipatterns in Test Automation
- [ ] **Orphaned test data on assertion failure:** Creating a resource via `POST` and chaining body assertions before assigning `customerId = ...extract().path("id")`, or extracting `.path("id")` without assigning it to `customerId`, prevents `@AfterEach` from deleting the resource when an assertion fails
- [ ] **Vacuous assertions:** Assertions that can never fail (e.g. `assertTrue(totalCustomers >= 0)` on a non-negative counter) — replace with meaningful assertions against known state
- [ ] **Inconsistent assertion libraries:** Mixing `org.junit.jupiter.api.Assertions.*` and Hamcrest `MatcherAssert.assertThat` / REST Assured `.body(...)` matchers arbitrarily within the same class
- [ ] **Hardcoded magic numbers / strings:** Repeated endpoint paths (`"/customers"`, `"/products"`), header names (`"Idempotency-Key"`), or limits that belong in constants or helper methods
- [ ] **Dead code or misleading comments:** Comments that contradict the actual assertion (e.g. comment says "assert the field is explicitly saved as an empty string" while code asserts `nullValue()`)

### Allure & Build Hygiene (`pom.xml`)
- [ ] JUnit Jupiter engine/params dependencies aligned with `junit-jupiter-api` version in `pom.xml`
- [ ] Allure annotations (`@Epic`, `@Feature`, `@Story`, `@Severity`, `@Description`, `@DisplayName`) used consistently where reporting metadata adds clarity

## Output Contract

When invoked by an agent that supplies a `feedback_path`, write findings to that file using **exactly** the format below. The first line of every finding **must** be `### [ ] F<N> · <Severity> · <Category>` — the `[ ]` checkbox is parsed downstream.

```markdown
# Code Review — Iteration <N>

**Target:** <path or scope>
**Date:** <YYYY-MM-DD>

## Summary

- Total findings: <N>
- Critical: <X> | High: <Y> | Medium: <Z> | Low: <W>

## Findings

### [ ] F1 · High · Java
**Location:** `src/test/java/com/automation/stripe/customers/CustomerEdgeCasesTest.java:48`
**Issue:** `createCustomerWithEmptyEmail_ShouldReturn200` extracts `"id"` from the `POST /customers` response without assigning it to `this.customerId`, and the class lacks an `@AfterEach` teardown method. The subsequent `deleteTestCustomer()` call is a no-op because `customerId` is `null`, permanently leaking the customer in Stripe.
**Fix:**
```java
@AfterEach
void tearDown() {
    deleteTestCustomer();
}

@Test
void createCustomerWithEmptyEmail_ShouldReturn200() {
    customerId = given()
            .spec(StripeSpecFactory.getBaseRequestSpec())
            .formParam("name", "no email user Bob")
            .formParam("email", "")
            .when()
            .post("/customers")
            .then()
            .spec(StripeSpecFactory.getSuccessResponseSpec())
            .body("email", nullValue())
            .extract().path("id");
}
```
```

Severity scale: `Critical` (data/resource leak in live Stripe env, broken build/test execution) · `High` (flaky test, false-positive assertion, broken test isolation) · `Medium` (Java/JUnit 5 idiom, maintainability, redundant API calls) · `Low` (naming typo, unused import, comment drift, polish).

When invoked **without** a `feedback_path` (interactive single-shot), produce the same content as plain output and end with a summary table by severity.

## Scope Discipline

- Read the target file(s) fully before flagging issues — partial reads produce false positives.
- Defer REST Assured DSL / request-response specification mechanics to `restassured-review`, secret/logging leaks to `security-audit`, and missing test scenarios to `coverage-analysis`. This skill owns Java 17 idioms, JUnit 5 lifecycle/isolation, assertion quality, and general test architecture.
- Do not propose refactors beyond what each finding requires.
