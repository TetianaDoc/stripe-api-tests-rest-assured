package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.TestInstance;

//Test suite simulates a scenario where create a user,
//and trying to retrive a user with invalid auth.

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerAuthErrorValidationTest extends BaseApiTest {

    @Test
    @Order(1)
    void createCustomer() {
        customerId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "refactored test user")
                .formParam("email", "refactored1@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", notNullValue())
                .extract().path("id");
    }

    @Test
    @Order(2)
    void retriveCustomer() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .get("/customers/" + customerId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", equalTo("refactored1@leovegas.com"));

    }

    @Test
    @Order(3)
    void getCustomers_WithInvalidAuth_ShouldReturn401() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec()) //Loads base URL and content type
                .auth().oauth2("sk_test_invalid_key") //Overwrites the valid token with the invalid one
                .when()
                .get("/customers")
                .then()
                .statusCode(401)
                .body("error.type", equalTo("invalid_request_error"));
    }

    @Test
    @Order(4)
    void deleteCustomer() {
        deleteTestCustomer();
    }
}
