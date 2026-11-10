package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

//Test simulates a scenario where trying to retrieve a user with invalid auth.

class CustomerAuthErrorValidationTest extends BaseApiTest {

    @Test
    void getCustomers_WithInvalidAuth_ShouldReturn401() {
        //Arrange
        setupTestCustomer("new customer Anna", "anna@leovegas.com");

        //Act and Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec()) //Loads base URL and content type
                .auth().oauth2("sk_test_invalid_key") //Overwrites the valid token with the invalid one
                .when()
                .get("/customers")
                .then()
                .statusCode(401)
                .body("error.type", equalTo("invalid_request_error"));
    }

    @AfterEach
    void tearDown() {
        deleteTestCustomer(); // Calls the inherited method from BaseApiTest
    }
}
