package com.automation.stripe.products;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

class ProductCrudIndependentTest extends BaseApiTest {
    @AfterEach
    void tearDown() {
        deleteTestProduct();
    }

    @Test
    void createProduct_ShouldReturnValidId() {
        // Act & Assert
        productId = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Enterprise API Framework Course")
                .formParam("description", "A premium course for SDETs")
                .when()
                .post("/products")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("id", notNullValue())
                .body("name", equalTo("Enterprise API Framework Course"))
                .extract().path("id");
    }

    @Test
    void retrieveProduct_ShouldReturnCorrectData() {
        // Arrange
        setupTestProduct("LeoVegas VIP Subscription");

        // Act & Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .when()
                .get("/products/" + productId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("name", equalTo("LeoVegas VIP Subscription"));
    }

    @Test
    void updateProduct_ShouldModifyExistingData() {
        // Arrange
        setupTestProduct("Legacy Product Name");

        // Act & Assert
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .formParam("name", "Updated Product Name")
                .formParam("active", "false") // Archiving the product
                .when()
                .post("/products/" + productId) // Stripe uses POST for updates
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
                .when()
                .delete("/products/" + productId)
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .body("deleted", equalTo(true));

        // Nullify the ID so @AfterEach doesn't fail trying to delete it again
        productId = null;
    }
}
