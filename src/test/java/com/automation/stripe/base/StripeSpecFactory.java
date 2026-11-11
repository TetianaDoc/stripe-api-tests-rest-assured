package com.automation.stripe.base;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import static io.restassured.RestAssured.oauth2;

public class StripeSpecFactory {

    private static String getValidatedSecretKey() {
        String key = System.getenv("STRIPE_SECRET_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("STRIPE_SECRET_KEY environment variable must be set");
        }
        if (key.startsWith("sk_live_") || key.startsWith("rk_live_")) {
            throw new IllegalStateException("Live Stripe keys (sk_live_/rk_live_) are not permitted in automated test runs");
        }
        return key;
    }

    //Request configuration
    public static RequestSpecification getBaseRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri("https://api.stripe.com/v1")
                .setConfig(RestAssuredConfig.config()
                        .logConfig(LogConfig.logConfig().blacklistHeader("Authorization")))
                .setAuth(oauth2(getValidatedSecretKey()))
                .setContentType(ContentType.URLENC)
                .log(LogDetail.ALL)
                .build();
    }

    public static RequestSpecification getRequestSpecWithKey(String apiKey) {
        return new RequestSpecBuilder()
                .setBaseUri("https://api.stripe.com/v1")
                .setConfig(RestAssuredConfig.config()
                        .logConfig(LogConfig.logConfig().blacklistHeader("Authorization")))
                .setAuth(oauth2(apiKey))
                .setContentType(ContentType.URLENC)
                .log(LogDetail.ALL)
                .build();
    }

    //Response configuration
    public static ResponseSpecification getSuccessResponseSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectContentType(ContentType.JSON)
                .log(LogDetail.BODY)
                .build();
    }
}
