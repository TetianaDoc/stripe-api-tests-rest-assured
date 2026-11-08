import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

//Test suite simulates a scenario where create a user,
// modify their data,
// retrieve it to verify,
// and clean up by deleting it.

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CustomerCrudFlow {
    private static final String STRIPE_SECRET_KEY = System.getenv("STRIPE_SECRET_KEY");
    private static String customerId; //Store id to use in next tests

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "https://api.stripe.com/v1";
    }

    @Test
    @Order(1)
    void createCustomer() {
        Response response = given()
                .auth().oauth2(STRIPE_SECRET_KEY) //Sends the bearer token
                .contentType("application/x-www-form-urlencoded")
                .formParam("name", "Automation test user")
                .formParam("email", "auto@leovegas.com")
                .log().all()
                .when()
                .post("/customers")
                .then()
                .log().all()
                .statusCode(200)
                .body("id", notNullValue())
                .extract().response();
        customerId = response.path("id");
    }

    @Test
    @Order(2)
    void retriveCustomer() {
        given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .when()
                .get("/customers/" + customerId)
                .then()
                .statusCode(200)
                .body("email", equalTo("auto@leovegas.com"));
    }

    @Test
    @Order(3)
    public void updateCustomer() {
        given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .contentType("application/x-www-form-urlencoded")
                .formParam("name", "updated user")
                .when()
                .post("/customers/" + customerId)
                .then()
                .statusCode(200)
                .body("name", equalTo("updated user"));
    }

    @Test
    @Order(4)
    public void deleteCustomer() {
        given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .when()
                .delete("/customers/" + customerId)
                .then()
                .statusCode(200)
                .body("deleted", equalTo(true));
    }
}
