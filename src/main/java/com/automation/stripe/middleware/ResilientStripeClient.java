package com.automation.stripe.middleware;

import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

 /*
 * This class serves as an in-process simulation layer for standalone test suite execution without requiring
 * an external microservice runtime.
 */
public class ResilientStripeClient {
    private int consecutiveFailures = 0;
    private boolean circuitOpen = false;
    private static final int FAILURE_THRESHOLD = 3;
    private static final long TIMEOUT_MS = 2000; // 2 second timeout limit

    public Response getProducts(String baseUri) {
        // 1. FAIL FAST: If the circuit is open, reject immediately without a network call
        if (circuitOpen) {
            throw new IllegalStateException("503 Service Unavailable: Circuit Breaker is OPEN");
        }

        // 2. Make the actual network call to Stripe (or WireMock)
        Response response = given()
                .baseUri(baseUri)
                .when()
                .get("/products");

        // 3. Evaluate the response. If it takes too long (latency), count it as a failure.
        if (response.time() > TIMEOUT_MS || response.statusCode() >= 500) {
            consecutiveFailures++;
            if (consecutiveFailures >= FAILURE_THRESHOLD) {
                circuitOpen = true; // Trip the breaker
            }
        } else {
            consecutiveFailures = 0; // Reset on success
        }

        return response;
    }
}
