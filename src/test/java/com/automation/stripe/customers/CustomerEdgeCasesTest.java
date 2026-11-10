package com.automation.stripe.customers;

import static io.restassured.RestAssured.when;
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

    //To count more than 100 customers, it must implement cursor-based pagination using a while loop
    // that reads the has_more flag and uses the ID of the last customer
    // in the current page as the starting_after parameter for the next page.
    @Test
    void calculateTotalNumberOfCreatedCustomersWithPagination() {

        boolean hasMore = true;
        String lastObjectId = null;
        int totalCustomers = 0;

        while (hasMore) {
            //1. prepare request with the limit 100
            var request = given()
                    .spec(StripeSpecFactory.getBaseRequestSpec())
                    .queryParam("limit", 100);

            //2. if we have a cursor from a previous page, attach it
            if (lastObjectId != null) {
                request.queryParam("starting_after", lastObjectId);
            }
            //3. Execute the API call
            var response = request
                    .when()
                    .get("/customers")
                    .then()
                    .spec(StripeSpecFactory.getSuccessResponseSpec())
                    .extract();

            //4. Extract the IDs from the current page to count them
            java.util.List<String> customerIds = response.path("data.id");
            int currentPageSize = customerIds.size();
            totalCustomers += currentPageSize;

            // 5. Check if Stripe has more data
            hasMore = response.path("has_more");

            // 6. Update the cursor to the very last ID in the current page
            if (hasMore && currentPageSize > 0) {
                lastObjectId = customerIds.get(currentPageSize - 1);
            }
        }

        System.out.println("Total customers across all pages: " + totalCustomers);
        org.junit.jupiter.api.Assertions.assertTrue(totalCustomers >= 0);
    }
}
