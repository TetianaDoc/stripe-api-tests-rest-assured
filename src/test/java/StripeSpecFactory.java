import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

public class StripeSpecFactory {
    static final String STRIPE_SECRET_KEY = System.getenv("STRIPE_SECRET_KEY");

    //Request configuration
    static RequestSpecification getBaseRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri("https://api.stripe.com/v1")
                .setAuth(io.restassured.RestAssured.oauth2(STRIPE_SECRET_KEY))
                .setContentType("application/x-www-form-urlencoded")
                .log(LogDetail.ALL)
                .build();
    }

    //Response configuration
    static ResponseSpecification getSuccessResponseSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(200)
                .log(LogDetail.BODY)
                .build();
    }
}
