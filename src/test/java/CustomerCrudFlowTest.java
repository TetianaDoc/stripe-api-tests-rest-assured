
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.TestInstance;
//Test suite simulates a scenario where create a user,
// modify their data,
// retrieve it to verify,
// and clean up by deleting it.

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerCrudFlowTest extends BaseApiTest{

    @Test
    @Order(1)
    void createCustomer() {
        customerId = given() //assign to inherited customer id
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Refactored user")
                .formParam("email", "refactored@leovegas.com")
                .when()
                .post("/customers")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", notNullValue())
                .extract().path("id");
    }

    @Test
    @Order(2)
    void retriveCustomer() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .get("/customers/" + customerId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("email", equalTo("refactored@leovegas.com"));
    }

    @Test
    @Order(3)
    void updateCustomer() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "updated refactored user")
                .when()
                .post("/customers/" + customerId)
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
