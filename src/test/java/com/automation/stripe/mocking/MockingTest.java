package com.automation.stripe.mocking;

import com.automation.stripe.base.StripeSpecFactory;
import com.automation.stripe.middleware.ResilientStripeClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockingTest {
    private static WireMockServer wireMockServer;
    private static String mockBaseUrl;
    private static RequestSpecification mockRequestSpec;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        mockBaseUrl = "http://localhost:" + wireMockServer.port() + "/v1";
        mockRequestSpec = new RequestSpecBuilder()
                .setBaseUri(mockBaseUrl)
                .setContentType(ContentType.URLENC)
                .build();
    }

    @AfterEach
    void resetWireMock() {
        WireMock.reset();
    }

    @AfterAll
    static void tearDownWireMock() {
        wireMockServer.stop();
    }

    @Test
    void simulateServiceDown_ShouldReturn503AndHideStackTrace() {
        stubFor(post(urlEqualTo("/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withBody("{\"error\": {\"message\": \"Stripe Gateway is down\"}}")));

        given()
                .spec(mockRequestSpec)
                .when()
                .post("/customers")
                .then()
                .statusCode(503)
                .contentType(ContentType.JSON)
                .body("error.message", equalTo("Stripe Gateway is down"))
                .body(not(containsString("Exception")))
                .body(not(containsString("at com.")));
    }

    @Test
    void simulateNetworkLatency_ShouldDelayResponse() {
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withFixedDelay(5000)
                        .withBody("{\"object\": \"list\", \"data\": []}")));

        given()
                .spec(mockRequestSpec)
                .when()
                .get("/products")
                .then()
                .spec(StripeSpecFactory.getSuccessResponseSpec())
                .time(greaterThanOrEqualTo(5000L), TimeUnit.MILLISECONDS)
                .body("object", equalTo("list"));
    }

    @Test
    void circuitBreaker_ShouldTripAndFailFastOnRepeatedHttp500Errors() {
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withBody("{\"error\": {\"message\": \"Internal Server Error\"}}")));

        ResilientStripeClient client = new ResilientStripeClient();

        // 3 consecutive 500 errors reach FAILURE_THRESHOLD
        for (int i = 0; i < 3; i++) {
            Response response = client.getProducts(mockBaseUrl);
            assertEquals(500, response.statusCode());
        }

        // 4th request must fail fast with circuit breaker OPEN
        long startTime = System.currentTimeMillis();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.getProducts(mockBaseUrl)
        );
        long executionTime = System.currentTimeMillis() - startTime;

        assertTrue(executionTime < 100,
                "Expected instant failure under 100ms, but took " + executionTime + "ms");
        assertTrue(exception.getMessage().contains("Circuit Breaker is OPEN"));
    }

    @Test
    void simulateRateLimiting_ShouldReturn429WithRateLimitError() {
        stubFor(post(urlEqualTo("/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withHeader("Retry-After", "1")
                        .withBody("{\"error\": {\"type\": \"rate_limit_error\", \"message\": \"Too many requests hit the API too quickly.\"}}")));

        given()
                .spec(mockRequestSpec)
                .formParam("email", "rate_limit_test@example.com")
                .when()
                .post("/customers")
                .then()
                .statusCode(429)
                .header("Retry-After", equalTo("1"))
                .body("error.type", equalTo("rate_limit_error"))
                .body("error.message", containsString("Too many requests"));
    }

    @Test
    void circuitBreaker_ShouldResetConsecutiveFailuresOnSuccessfulResponse() {
        ResilientStripeClient client = new ResilientStripeClient();

        // 1. Simulate 2 transient downstream errors (below threshold of 3)
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withBody("{\"error\": {\"message\": \"Transient Error\"}}")));

        client.getProducts(mockBaseUrl);
        client.getProducts(mockBaseUrl);

        // 2. Service recovers: return 200 OK to reset consecutiveFailures to 0
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withBody("{\"object\": \"list\", \"data\": []}")));

        Response successResponse = client.getProducts(mockBaseUrl);
        assertEquals(200, successResponse.statusCode());

        // 3. Next failure should NOT trip the breaker because count was reset
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", ContentType.JSON.toString())
                        .withBody("{\"error\": {\"message\": \"Transient Error 2\"}}")));

        assertDoesNotThrow(() -> {
            Response r = client.getProducts(mockBaseUrl);
            assertEquals(500, r.statusCode());
        });
    }

    @Test
    void circuitBreaker_ShouldTripAndFailFastAfterRepeatedTimeouts() {
        // 1. Configure the downstream stub to hang indefinitely (simulating a dead service)
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000))); // 5-second delay

        ResilientStripeClient middleman = new ResilientStripeClient();

        // 2. Intentionally hit the API 3 times to trip the Circuit Breaker
        // (Each request takes 5 seconds, so this loop takes ~15 seconds total)
        for (int i = 0; i < 3; i++) {
            middleman.getProducts(mockBaseUrl);
        }

        // 3. The Circuit Breaker should now be OPEN.
        // The 4th request must be rejected instantly by the middleman, without hitting the WireMock.
        long startTime = System.currentTimeMillis();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> middleman.getProducts(mockBaseUrl)
        );

        long executionTime = System.currentTimeMillis() - startTime;

        // 4. Assert the response was fast (e.g., under 100ms), and returned the fallback error
        assertTrue(executionTime < 100,
                "Expected instant failure, but took " + executionTime + "ms");
        assertTrue(exception.getMessage().contains("Circuit Breaker is OPEN"));
    }
}
