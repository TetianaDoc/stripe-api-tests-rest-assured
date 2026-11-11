package com.automation.stripe.customers;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

//Test suite simulates a scenario with a flow, where create a user,
// modify their data,
// retrieve it to verify,
// and clean up by deleting it.
//It is for demonstrating @Order and @TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerCrudFlowTest extends BaseApiTest {

    @AfterAll
    void tearDownAll() {
        deleteTestCustomer();
    }

    @Test
    @Order(1)
    void createCustomer() {
        customerId = given() //assign to inherited customer id
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Refactored user")
                .formParam("email", "refactored@example.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", startsWith("cus_"))
                .extract().path("id");
    }

    @Test
    @Order(2)
    void retrieveCustomer() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", customerId)
                .when()
                .get("/customers/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", equalTo("refactored@example.com"));
    }

    @Test
    @Order(3)
    void updateCustomer() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", customerId)
                .formParam("name", "updated refactored user")
                .when()
                .post("/customers/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("updated refactored user"));
    }

    @Test
    @Order(4)
    void deleteCustomer() {
        deleteTestCustomer();
    }
}
