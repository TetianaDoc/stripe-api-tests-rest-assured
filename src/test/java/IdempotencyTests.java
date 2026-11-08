import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class IdempotencyTests {
    private static final String STRIPE_SECRET_KEY = System.getenv("STRIPE_SECRET_KEY");

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "https://api.stripe.com/v1";
    }

    @Test
    public void createCustomerTwiceWithSameIdempotencyKey_ShouldNotCreateDuplicates() {
        //1. Generate a unique key for this transaction
        String idempotencyKey = UUID.randomUUID().toString();
        System.out.println("Using Idempotency Key: " + idempotencyKey);

        //2. First request: The initial network call
        String customerId = given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/x-www-form-urlencoded")
                .formParam("name", "Idempotent User")
                .formParam("email", "idempotent@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .statusCode(200)
                .extract().path("id"); //Extract the generated id to compare later

        System.out.println("First request created customer ID: " + customerId);

        //3. Second request: simulated a retry due to a timeout
        given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/x-www-form-urlencoded")
                .formParam("name", "Idempotent User")
                .formParam("email", "idempotent@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .statusCode(200)
                //Stripe natively returns this header when it serves a cached response
                .header("Idempotent-Replayed", "true")
                //Assert that the exact same entity is returned, preventing a duplicate charge/customer
                .body("id", equalTo(customerId));
        System.out.println("Second request safely returned the cached response.");
    }
}
