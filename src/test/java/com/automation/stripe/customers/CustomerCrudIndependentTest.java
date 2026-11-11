package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

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
                .formParam("email", "indep@example.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", startsWith("cus_"))
                .extract().path("id");
    }

    @Test
    void retrieveCustomer_ShouldReturnValidData() {
        //Arrange
        setupTestCustomer("independent retrieve user", "indretruser@example.com");
        //Act and Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", customerId)
                .when()
                .get("/customers/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", equalTo("indretruser@example.com"));
    }

    @Test
    void updateCustomer_ShouldModifyData() {
        setupTestCustomer("old name", "oldemail@example.com");
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", customerId)
                .formParam("name", "updated name")
                .formParam("email", "updemail@example.com")
                .when()
                .post("/customers/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("updated name"))
                .body("email", equalTo("updemail@example.com"));
    }

    @Test
    void deleteCustomer_ShouldReturnTrue() {
        setupTestCustomer("to be deleted", "delete@example.com");
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", customerId)
                .when()
                .delete("/customers/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("deleted", equalTo(true));

        //Nullify the id, so @AfterEach teardown doesn't try to delete it second time
        customerId = null;
    }
}
