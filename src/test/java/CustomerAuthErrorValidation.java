
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

//Test suite simulates a scenario where create a user,
//and trying to retrive a user with invalid auth.

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CustomerAuthErrorValidation {
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
                .auth().oauth2(STRIPE_SECRET_KEY)
                .contentType("application/x-www-form-urlencoded")
                .formParam("name", "Automation test user 1")
                .formParam("email", "auto1@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract().response();
        customerId = response.path("id");
    }

    @Test
    @Order(2)
    void retriveCustomer(){
        given()
                .auth().oauth2(STRIPE_SECRET_KEY)
                .when()
                .get("/customers/" + customerId)
                .then()
                .statusCode(200)
                .body("email", equalTo("auto1@leovegas.com"));

    }

    @Test
    @Order(3)
    void getCustomers_WithInvalidAuth_ShouldReturn401() {
        given()
                .auth().oauth2("sk_test_invalid_key")
                .when()
                .get("/customers")
                .then()
                .statusCode(401)
                .body("error.type", equalTo("invalid_request_error"));
    }
}
