package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class CustomerCrudIndependentTest extends BaseApiTest {

    //Teardown - runs after each test to clean up
    @AfterEach
    void tearDown() {
        deleteTestCustomer(); // Calls the inherited method from BaseApiTest
    }

    @Test
    void createCustomer_ShouldReturnValidId() {
        // Act & Assert
        customerId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Created independent user")
                .formParam("email", "indep@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", notNullValue())
                .extract().path("id");
    }

    @Test
    void createCustomer_ShouldReturnValidData() {
        //Arrange
        setupTestCustomer("independent retrieve user", "indretruser@leovegas.com");
        //Act and Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .get("/customers/" + customerId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", equalTo("indretruser@leovegas.com"));
    }

    @Test
    void updateCustomer_ShouldMofifyData() {
        setupTestCustomer("old name", "oldemail@leovegas.com");
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "updated name")
                .formParam("email", "updemail@leovegas.com")
                .when()
                .post("/customers/" + customerId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("updated name"))
                .body("email", equalTo("updemail@leovegas.com"));
    }

    @Test
    void deleteCustomer_ShouldReturnTrue() {
        setupTestCustomer("to be deleted", "delete@leovegas.com");
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .delete("/customers/" + customerId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("deleted", equalTo(true));

        //Nullify the id, so @AfterEach teardown doesn't try to delete it second time
        customerId = null;
    }
}
