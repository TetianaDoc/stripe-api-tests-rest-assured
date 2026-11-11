package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

//Test simulates a scenario where trying to retrieve a user with invalid auth.

class CustomerAuthErrorValidationTest extends BaseApiTest {

    @Test
    void getCustomers_WithInvalidAuth_ShouldReturn401() {
        given()
                .spec(StripeSpecFactory.getRequestSpecWithKey("sk_test_invalid_key"))
                .when()
                .get("/customers")
                .then()
                .statusCode(401)
                .body("error.type", equalTo("invalid_request_error"))
                .body("error.message", notNullValue());
    }
}
