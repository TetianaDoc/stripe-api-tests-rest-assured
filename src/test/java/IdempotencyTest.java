import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class IdempotencyTest extends BaseApiTest {

    @Test
    void createCustomerTwiceWithSameIdempotencyKey_ShouldNotCreateDuplicates() {
        //1. Generate a unique key for this transaction
        String idempotencyKey = UUID.randomUUID().toString();
        System.out.println("Using Idempotency Key: " + idempotencyKey);

        //2. First request: The initial network call
        customerId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .header("Idempotency-Key", idempotencyKey)
                .formParam("name", "Idempotent Refactored User")
                .formParam("email", "idempotent_ref@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .extract().path("id"); //Extract the generated id to compare later

        System.out.println("First request created customer ID: " + customerId);

        //3. Second request: simulated a retry due to a timeout
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .header("Idempotency-Key", idempotencyKey)
                .formParam("name", "Idempotent Refactored User")
                .formParam("email", "idempotent_ref@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                //Stripe natively returns this header when it serves a cached response
                .header("Idempotent-Replayed", "true")
                //Assert that the exact same entity is returned, preventing a duplicate charge/customer
                .body("id", equalTo(customerId));
        System.out.println("Second request safely returned the cached response.");
    }

    // 4. Call the parent cleanup method
    @AfterEach
    void tearDown() {
        deleteTestCustomer();
    }
}

