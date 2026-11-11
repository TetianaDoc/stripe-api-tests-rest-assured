---
name: security-audit
description: Security and secret-hygiene audit for this Stripe API test automation codebase (REST Assured + Maven + Allure). Covers API key sourcing, fail-fast credential checks, Authorization header blacklisting in REST Assured and Allure logs, PII/email domain safety in test fixtures, and dependency hygiene. Use when reviewing spec factories, base test classes, test suites, or Maven config.
---

# Security Audit Skill — Stripe API Tests

Apply this security checklist to the target file, package, or `pom.xml`. Be strict on secret leakage and live-API safety: API test suites frequently leak live or test Bearer tokens (`sk_test_...`, `sk_live_...`, `rk_...`) into CI console logs and Allure HTML reports when REST Assured request logging is unmasked.

## Audit Scope

### 1. API Key & Credential Hygiene
- [ ] **No hardcoded secrets:** Stripe keys (`sk_test_*`, `sk_live_*`, `rk_test_*`, `rk_live_*`, `whsec_*`) must never appear in source code, `pom.xml`, or committed properties files. The only permitted literal token in test code is an obviously synthetic invalid token (e.g. `"sk_test_invalid_key"`) used exclusively in `401 Unauthorized` negative tests.
- [ ] **Fail-fast environment variable validation:** `StripeSpecFactory` must validate that `STRIPE_SECRET_KEY` is non-null and non-blank before passing it to `RestAssured.oauth2(...)`, throwing a descriptive `IllegalStateException("STRIPE_SECRET_KEY environment variable must be set")` instead of failing with a cryptic NullPointerException or sending `Bearer null`.
- [ ] **Live-key guard (safety rail):** `StripeSpecFactory` should reject `sk_live_` / `rk_live_` keys in automated test runs (e.g. require key to start with `sk_test_` or `rk_test_`) so destructive CRUD/delete tests can never accidentally run against a production Stripe account.

### 2. REST Assured & Allure Log Masking (Secret Leakage)
- [ ] **Blacklist `Authorization` header in REST Assured logs:** Using `.log(LogDetail.ALL)` or `.log(LogDetail.HEADERS)` on `RequestSpecBuilder` without `LogConfig.logConfig().blacklistHeader("Authorization")` prints `Authorization: Bearer sk_test_...` in plain text to stdout and CI logs.
  ```java
  .setConfig(RestAssuredConfig.config()
          .logConfig(LogConfig.logConfig()
                  .blacklistHeader("Authorization")
                  .enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL)))
  ```
- [ ] **Prefer logging on validation failure:** Avoid unconditional `.log(LogDetail.ALL)` on every request/response in CI; prefer `.log(LogDetail.URI)` or `enableLoggingOfRequestAndResponseIfValidationFails()` paired with blacklisted `Authorization` headers.
- [ ] **No sensitive tokens in `System.out.println`:** Never print API keys, raw `Authorization` headers, or full customer payloads containing PII to stdout.

### 3. Test Data & PII Hygiene
- [ ] **Safe email domains (RFC 2606):** Test fixtures creating Stripe customers must use reserved test domains (`@example.com`, `@test.example.com`) rather than real corporate domains (e.g. `@leovegas.com`, `@gmail.com`) to prevent accidental email delivery or third-party webhook leaks if Stripe email notifications are enabled.
- [ ] **Synthetic customer data:** Never use real person names, real phone numbers, or real payment card numbers (use only official Stripe test tokens/cards if payment methods are tested).

### 4. Repository & Build Hygiene (`.gitignore` & `pom.xml`)
- [ ] `.gitignore` excludes `target/`, `.idea/`, `*.iml`, `.env`, `*.local.properties`, `.claude/.review-cache/`, and `.agents/.review-cache/`.
- [ ] Dependencies in `pom.xml` use pinned release versions (no `LATEST` or `RELEASE` dynamic ranges) and `test` scope for test-only libraries (`rest-assured`, `junit-jupiter-api`, `allure-junit5`, `jackson-databind` if only used in `src/test`).

## Severity Classification

| Severity | Examples |
|---|---|
| **Critical** | Hardcoded real Stripe key (`sk_test_...` / `sk_live_...`), unmasked `Authorization` Bearer header logged via `LogDetail.ALL` in `StripeSpecFactory`, missing `sk_live_` production guard |
| **High** | Missing null/blank check on `STRIPE_SECRET_KEY` env var in `StripeSpecFactory`, `.env` or `.review-cache` not gitignored |
| **Medium** | Real corporate email domain (`@leovegas.com`) used in customer creation fixtures instead of `@example.com`, unconditional verbose body logging |
| **Low** | Missing `<scope>test</scope>` on test-only dependencies in `pom.xml`, minor hardening |

## Output Contract

When invoked by an agent with a `feedback_path`, write findings to the feedback file using the shared format:

```markdown
### [ ] F<N> · <Severity> · Security
**Location:** `src/test/java/com/automation/stripe/base/StripeSpecFactory.java:18`
**Issue:** `RequestSpecBuilder` enables `.log(LogDetail.ALL)` without blacklisting the `Authorization` header, leaking the raw `STRIPE_SECRET_KEY` Bearer token to console and CI logs on every request.
**Fix:**
```java
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;

public static RequestSpecification getBaseRequestSpec() {
    return new RequestSpecBuilder()
            .setBaseUri("https://api.stripe.com/v1")
            .setConfig(RestAssuredConfig.config()
                    .logConfig(LogConfig.logConfig().blacklistHeader("Authorization")))
            .setAuth(io.restassured.RestAssured.oauth2(getValidatedSecretKey()))
            .setContentType(ContentType.URLENC)
            .log(LogDetail.ALL)
            .build();
}
```
```

`<Category>` is always `Security`.

## Scope Discipline

- Own credential handling, header/log masking, live-environment guards, PII/email domain hygiene in test data, and build/gitignore security.
- Defer general Java style and JUnit lifecycle to `code-review` and REST Assured DSL mechanics to `restassured-review`.
