package com.automation.stripe.products;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;

class ProductCrudIndependentTest extends BaseApiTest {

    @AfterEach
    void tearDown() {
        deleteTestProduct();
    }

    @Test
    void createProduct_ShouldReturnValidId() {
        var response = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Enterprise API Framework Course")
                .formParam("description", "A premium course for SDETs")
                .when()
                .post("/products")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .extract().response();

        productId = response.path("id");

        response.then()
                .body("id", startsWith("prod_"))
                .body("name", equalTo("Enterprise API Framework Course"));
    }

    @Test
    void createProductWithoutName_ShouldReturn400() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("description", "Product missing required name")
                .when()
                .post("/products")
                .then()
                .statusCode(400)
                .body("error.type", equalTo("invalid_request_error"))
                .body("error.param", equalTo("name"));
    }

    @Test
    void retrieveProduct_ShouldReturnCorrectData() {
        // Arrange
        setupTestProduct("LeoVegas VIP Subscription");

        // Act & Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", productId)
                .when()
                .get("/products/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("LeoVegas VIP Subscription"));
    }

    @Test
    void retrieveProductWithInvalidId_ShouldReturn404() {
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("productId", "prod_nonexistent12345")
                .when()
                .get("/products/{productId}")
                .then()
                .statusCode(404)
                .body("error.type", equalTo("invalid_request_error"))
                .body("error.message", containsString("No such product"));
    }

    @Test
    void updateProduct_ShouldModifyExistingData() {
        // Arrange
        setupTestProduct("Legacy Product Name");

        // Act & Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", productId)
                .formParam("name", "Updated Product Name")
                .formParam("active", "false") // Archiving the product
                .when()
                .post("/products/{id}") // Stripe uses POST for updates
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("Updated Product Name"))
                .body("active", equalTo(false));
    }

    @Test
    void deleteProduct_ShouldReturnDeletedTrue() {
        // Arrange
        setupTestProduct("Temporary Test Product");

        // Act & Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .pathParam("id", productId)
                .when()
                .delete("/products/{id}")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("deleted", equalTo(true));

        // Nullify the ID so @AfterEach doesn't fail trying to delete it again
        productId = null;
    }

    @Test
    void listProductsWithLimit_ShouldReturnValidList() {
        int limit = 3;
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .queryParam("limit", limit)
                .when()
                .get("/products")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("object", equalTo("list"))
                .body("data", hasSize(lessThanOrEqualTo(limit)));
    }
}
