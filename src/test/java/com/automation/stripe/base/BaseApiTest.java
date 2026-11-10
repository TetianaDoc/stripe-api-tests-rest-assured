package com.automation.stripe.base;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class BaseApiTest {
    // Protected - child classes can read and write to it
    public String customerId;

    // A reusable cleanup method, NOT annotated with @AfterEach so child classes can control it
    public void deleteTestCustomer() {
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
}
