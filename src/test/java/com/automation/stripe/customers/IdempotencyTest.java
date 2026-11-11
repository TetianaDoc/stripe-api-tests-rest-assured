package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

class IdempotencyTest extends BaseApiTest {

    @Test
    void createCustomerTwiceWithSameIdempotencyKey_ShouldNotCreateDuplicates() {
        //1. Generate a unique key for this transaction
        String idempotencyKey = UUID.randomUUID().toString();

        //2. First request: Use the overloaded method from base class
        setupTestCustomer("Idempotent Refactored User 1", "idempotent_ref1@example.com", idempotencyKey);

        // 3. Second request: Simulated retry
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .header("Idempotency-Key", idempotencyKey)
                .formParam("name", "Idempotent Refactored User 1")
                .formParam("email", "idempotent_ref1@example.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .header("Idempotent-Replayed", "true")
                .body("id", equalTo(customerId));
    }

    @Test
    void createCustomerWithSameIdempotencyKeyButDifferentPayload_ShouldReturn400() {
        //1. Generate key
        String idempotencyKey = UUID.randomUUID().toString();

        //2. First request - use overloaded setup method from base class
        setupTestCustomer("Original user", "original@example.com", idempotencyKey);

        //3. Simulate malformed retry: same key, but changed email
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .header("Idempotency-Key", idempotencyKey) // Reusing the key
                .formParam("name", "Original user")
                .formParam("email", "hacked_email@example.com") // Changed payload!
                .when()
                .post("/customers")
                .then()
                .statusCode(400) // Stripe rejects payload modification on replayed keys
                .body("error.type", equalTo("idempotency_error"))
                .body("error.message", containsString("Keys for idempotent requests can only be used with the same parameters"));
    }

    // Call the parent cleanup method
    @AfterEach
    void tearDown() {
        deleteTestCustomer();
    }
}
