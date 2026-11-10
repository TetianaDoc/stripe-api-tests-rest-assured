package com.automation.stripe.customers;

import static org.hamcrest.Matchers.*;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

class CustomerEdgeCasesTest extends BaseApiTest {

    @Test
    void retrieveCustomerWithInvalidId_ShouldReturn404() {
        //How API handles the fake customer id
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .get("/customers/cus_invalidID")
                .then()
                .statusCode(404)
                .body("error.type", equalTo("invalid_request_error"))
                .body("error.message", containsString("No such customer"));

    }

    @Test
    void listCustomersWithLimitQueryParameter_ShouldReturnCorrectSize() {
        //Test API pagination by passing query parameter
        int limit = 3;
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .queryParam("limit", limit)
                .when()
                .get("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                //verifies tha data arrey contains 3 or less items
                .body("data", hasSize(lessThanOrEqualTo(limit)))
                .body("object", equalTo("list"));
    }

    @Test
    void createCustomerWithEmptyEmail_ShouldReturn200() {
        // Tests API validation rules. Stripe allows empty emails,
        // but we want to assert the field is explicitly saved as an empty string.
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "no email user Bob")
                .formParam("email", "")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", nullValue())
                .extract().path("id");

        //clean up the customer after test
        deleteTestCustomer();
    }
}
