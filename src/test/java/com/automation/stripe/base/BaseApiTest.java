package com.automation.stripe.base;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class BaseApiTest {
    // Protected - child classes can read and write to it
    protected String customerId;
    protected String productId;

    //Centralized setup customer method
    protected void setupTestCustomer(String name, String email) {
        customerId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", name)
                .formParam("email", email)
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .extract().path("id");
    }

    //Centralized setup product method
    protected void setupTestProduct(String name) {
        productId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", name)
                .when()
                .post("/products")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .extract().path("id");
    }

    // Overloaded centralized setup customer method for idempotency testing
    protected void setupTestCustomer(String name, String email, String idempotencyKey) {
        customerId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .header("Idempotency-Key", idempotencyKey)
                .formParam("name", name)
                .formParam("email", email)
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .extract().path("id");
    }

    // Existing Centralized Teardown Delete Customer Method
    protected void deleteTestCustomer() {
        if (customerId != null) {
            given()
                    .spec(StripeSpecFactory.getBaseRequestSpec())
                    .when()
                    .delete("/customers/" + customerId)
                    .then()
                    .spec(StripeSpecFactory.getSuccessResponseSpec())
                    .body("deleted", equalTo(true));
            System.out.println("Base Class Cleanup: Deleted customer " + customerId);
            customerId = null; // Reset to prevent double-deletion
        }
    }

    // Existing Centralized Teardown Delete Product Method
    protected void deleteTestProduct() {
        if (productId != null) {
            given()
                    .spec(StripeSpecFactory.getBaseRequestSpec())
                    .when()
                    .delete("/products/" + productId)
                    .then()
                    .spec(StripeSpecFactory.getSuccessResponseSpec())
                    .body("deleted", equalTo(true));

            productId = null;
        }
    }
}
