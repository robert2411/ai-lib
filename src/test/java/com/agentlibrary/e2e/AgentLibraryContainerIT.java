package com.agentlibrary.e2e;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests using Testcontainers.
 * Spins up the agent-library Docker image and tests REST API operations.
 *
 * <p>Tests are skipped gracefully when Docker is not available (e.g., CI without Docker daemon).
 * Run with: ./mvnw verify
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentLibraryContainerIT {

    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASS = "changeme";
    private static final int CONTAINER_PORT = 8080;

    private GenericContainer<?> container;
    private String baseUrl;
    private HttpClient httpClient;

    @BeforeAll
    void setUp() {
        // Skip gracefully when Docker is not available
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping E2E tests"
        );

        // Build image from project Dockerfile
        ImageFromDockerfile image = new ImageFromDockerfile("agent-library-it", false)
                .withDockerfilePath("Dockerfile")
                .withFileFromPath(".", Path.of("."));

        container = new GenericContainer<>(image)
                .withExposedPorts(CONTAINER_PORT)
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(120)))
                .withEnv("LIBRARY_DATA_DIR", "/data");

        container.start();

        int mappedPort = container.getMappedPort(CONTAINER_PORT);
        String host = container.getHost();
        baseUrl = String.format("http://%s:%d", host, mappedPort);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterAll
    void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    // === Helper Methods ===

    private String basicAuthHeader(String user, String pass) {
        String credentials = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", basicAuthHeader(DEFAULT_USER, DEFAULT_PASS))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", basicAuthHeader(DEFAULT_USER, DEFAULT_PASS))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", basicAuthHeader(DEFAULT_USER, DEFAULT_PASS))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", basicAuthHeader(DEFAULT_USER, DEFAULT_PASS))
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postFormNoAuth(String path, String formData)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // === CRUD Lifecycle Test (AC #2) ===

    @Test
    @Order(1)
    void testCrudLifecycle() throws IOException, InterruptedException {
        String testArtifact = """
                ---
                name: e2e-test-skill
                type: skill
                version: 1.0.0
                description: E2E test artifact
                harnesses:
                  - claude
                tags:
                  - test
                ---
                # E2E Test Skill
                
                This is a test artifact for E2E integration testing.
                """;

        // CREATE: POST artifact
        HttpResponse<String> createResponse = post("/api/v1/artifacts", testArtifact, "text/plain");
        assertEquals(201, createResponse.statusCode(),
                "POST should return 201 Created. Body: " + createResponse.body());

        // READ: GET specific version
        HttpResponse<String> getResponse = get("/api/v1/artifacts/e2e-test-skill/1.0.0");
        assertEquals(200, getResponse.statusCode(),
                "GET specific version should return 200");
        assertTrue(getResponse.body().contains("E2E Test Skill"),
                "Response body should contain artifact content");

        // UPDATE: PUT new version
        String updatedArtifact = """
                ---
                name: e2e-test-skill
                type: skill
                version: 1.1.0
                description: E2E test artifact updated
                harnesses:
                  - claude
                tags:
                  - test
                ---
                # E2E Test Skill v1.1.0
                
                Updated content for E2E testing.
                """;
        HttpResponse<String> putResponse = put("/api/v1/artifacts/e2e-test-skill/1.1.0",
                updatedArtifact, "text/plain");
        assertTrue(putResponse.statusCode() == 200 || putResponse.statusCode() == 201,
                "PUT should return 200 or 201. Got: " + putResponse.statusCode());

        // READ: GET latest should be 1.1.0
        HttpResponse<String> latestResponse = get("/api/v1/artifacts/e2e-test-skill/latest");
        assertEquals(200, latestResponse.statusCode(),
                "GET latest should return 200");
        assertTrue(latestResponse.body().contains("1.1.0") || latestResponse.body().contains("v1.1.0"),
                "Latest version should reference 1.1.0");

        // DELETE
        HttpResponse<String> deleteResponse = delete("/api/v1/artifacts/e2e-test-skill");
        assertEquals(204, deleteResponse.statusCode(),
                "DELETE should return 204 No Content. Got: " + deleteResponse.statusCode()
                        + " Body: " + deleteResponse.body());

        // VERIFY DELETED: GET should return 404
        HttpResponse<String> deletedResponse = get("/api/v1/artifacts/e2e-test-skill/latest");
        assertEquals(404, deletedResponse.statusCode(),
                "GET deleted artifact should return 404");
    }

    // === Search Test (AC #3) ===

    @Test
    @Order(2)
    void testSearchReturnsSeededArtifact() throws IOException, InterruptedException {
        // The bootstrapper seeds agent-lib-sh on first run
        HttpResponse<String> response = get("/api/v1/artifacts?q=agent-lib");
        assertEquals(200, response.statusCode(),
                "Search should return 200");

        String body = response.body();
        // Response uses .items field (per plan reviewer advisory)
        // Check both .items and .content to handle both API shapes
        assertTrue(body.contains("agent-lib-sh"),
                "Search results should contain seeded artifact 'agent-lib-sh'. Body: " + body);
    }

    // === Rate Limit Test (AC #4) ===

    @Test
    @Order(3)
    void testRateLimitExceedsLoginAttempts() throws IOException, InterruptedException {
        // Bucket capacity is 10, so 11th attempt should be rate-limited
        int successCount = 0;
        int rateLimitedAt = -1;

        for (int i = 1; i <= 12; i++) {
            HttpResponse<String> response = postFormNoAuth("/login",
                    "username=admin&password=wrong-password-" + i);

            int status = response.statusCode();
            if (status == 429) {
                rateLimitedAt = i;
                break;
            }
            // Expect 401 or 302 (redirect to login page with error) for wrong credentials
            assertTrue(status == 401 || status == 302 || status == 403,
                    "Wrong password attempt " + i + " should get 401/302/403, got: " + status);
            successCount++;
        }

        // Should hit rate limit at attempt 11 (bucket capacity = 10)
        assertTrue(rateLimitedAt > 0, "Should have been rate-limited after 10 attempts");
        assertTrue(rateLimitedAt <= 11,
                "Rate limit should kick in at or before attempt 11, but was at: " + rateLimitedAt);
        assertEquals(10, successCount,
                "Should have made exactly 10 successful (non-rate-limited) attempts");
    }
}
