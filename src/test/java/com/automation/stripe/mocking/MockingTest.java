package com.automation.stripe.mocking;

import com.automation.stripe.base.BaseApiTest;
import com.automation.stripe.base.StripeSpecFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static io.restassured.RestAssured.given;

class MockingTest extends BaseApiTest {
    private static WireMockServer wireMockServer;
    private static final String MOCK_BASE_URL = "http://localhost:8080/v1";

    @BeforeAll
    static void setupWireMock() {
        // Start the fake server on port 8080
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);
    }

    @AfterAll
    static void TearDownWireMock() {
        wireMockServer.stop();
    }

    @Test
    void simulateServiceDown_ShouldReturn503AndHideStackTrace() {
        // Mocking a downstream service failure
        // Configure the stub to intentionally return a 503 Service Unavailable
        stubFor(post(urlEqualTo("/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": {\"message\": \"Stripe Gateway is down\"}}")));

        // Verify the API handles the failure gracefully
        given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .baseUri(MOCK_BASE_URL) // Overrides the real Stripe URL to hit WireMock
                .when()
                .post("/customers")
                .then()
                .statusCode(503) // Expect 503, not a 500 Internal Server Error
                .body("error.message", equalTo("Stripe Gateway is down"))
                // Error Isolation. Ensure no raw stack traces leak to the client,
                // no raw stack trace in the response body  (java.lang.Exception)
                .body(not(containsString("Exception")))
                .body(not(containsString("at com.")));
    }

    @Test
    void simulateNetworkLatency_ShouldTestCircuitBreaker() {
        // Circuit Breaker / Timeouts
        // Configure a stub that simulates a hanging service (responds after 5 seconds)
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000) // Artificial network latency
                        .withBody("{\"object\": \"list\", \"data\": []}")));

        // Measure the response time.
        // In a real microservice, your API would fail with a 504 Gateway Timeout after 1-2 seconds,
        // or trigger a Circuit Breaker. Here, we verify the mock's ability to delay the network.
        long responseTime = given()
                .spec(StripeSpecFactory.getBaseRequestSpec())
                .baseUri(MOCK_BASE_URL)
                .when()
                .get("/products")
                .time();

        // Verify the response actually took at least 5 seconds
        org.junit.jupiter.api.Assertions.assertTrue(responseTime >= 5000,
                "Response time should be at least 5000ms due to WireMock delay");
    }

    @Test
    void circuitBreaker_ShouldTripAndFailFastAfterRepeatedTimeouts() {
        // 1. Configure the downstream stub to hang indefinitely (simulating a dead service)
        stubFor(get(urlEqualTo("/v1/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000))); // 5-second delay

        com.automation.stripe.middleware.ResilientStripeClient middleman =
                new com.automation.stripe.middleware.ResilientStripeClient();

        // 2. Intentionally hit the API 3 times to trip the Circuit Breaker
        // (Each request takes 5 seconds, so this loop takes ~15 seconds total)
        for (int i = 0; i < 3; i++) {
            middleman.getProducts(MOCK_BASE_URL);
        }

        // 3. The Circuit Breaker should now be OPEN.
        // The 4th request must be rejected instantly by the middleman, without hitting the WireMock.
        long startTime = System.currentTimeMillis();

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> middleman.getProducts(MOCK_BASE_URL)
        );

        long executionTime = System.currentTimeMillis() - startTime;

        // 4. Assert the response was fast (e.g., under 100ms), and returned the fallback error
        org.junit.jupiter.api.Assertions.assertTrue(executionTime < 100,
                "Expected instant failure, but took " + executionTime + "ms");
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("Circuit Breaker is OPEN"));
    }
}
