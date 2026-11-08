import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

public class ReqResTests {
    @BeforeAll
    public static void setup() {
        // Set the base URI for all tests in this class
        RestAssured.baseURI = "https://reqres.in/api";
    }

    @Test
    public void getSingleUser_ShouldReturn200AndCorrectName() {
        given()
                .log().uri() //Logs the request URI to the console
                .when()
                .get("/users/2")
                .then()
                .log().status()
                .statusCode(200)
                .time(lessThan(3000L)) //Assert response time is under 3 seconds
                .body("data.first_name", equalTo("Janet"))
                .body("data.last_name", equalTo("Weaver"));
    }

    @Test
    public void createUser_ShouldReturn201() {
        String requestBody = """
                {
                "name": "morpheus",
                "job": "leader"
                }
                """;
        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .log().all() //Logs full request details
                .when()
                .post("/users")
                .then()
                .log().body() //Logs the response body
                .statusCode(201)
                .body("name", equalTo("morpheus"));
    }
}
