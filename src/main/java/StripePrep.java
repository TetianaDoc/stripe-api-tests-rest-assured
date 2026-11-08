import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StripePrep {

    // 1. Records: Immutable data carriers perfect for concise JSON API payloads
    public record CustomerPayload(String email, String name, String idempotencyKey) {
    }

    public static void main(String[] args) {
        // Create test payloads
        var customers = List.of(
                new CustomerPayload("alice@test.com", "Alice", "uuid-1234"),
                new CustomerPayload("bob@test.com", "Bob", "uuid-5678"),
                new CustomerPayload("invalid-email", "Charlie", "uuid-9999")
        );

        // 2. Streams: Filter and process data declaratively
        List<CustomerPayload> validCustomers = customers.stream()
                .filter(c -> c.email().contains("@"))
                .toList();

        System.out.println("Valid customers: " + validCustomers);

        // 3. CompletableFuture: Simulate asynchronous API calls
        System.out.println("\nStarting async mock API calls...");

        List<CompletableFuture<String>> futures = validCustomers.stream()
                .map(customer -> CompletableFuture.supplyAsync(() -> mockApiCall(customer)))
                .toList();

        // Wait for all async calls to finish and print the results
        futures.forEach(future -> System.out.println(future.join()));
    }

    // Mock method to simulate a delayed POST request to Stripe
    private static String mockApiCall(CustomerPayload payload) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        return "Created customer: " + payload.name() + " (Key: " + payload.idempotencyKey() + ")";
    }
}