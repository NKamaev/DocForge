package com.docforge.e2e.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for test data setup / teardown via the backend REST API.
 * Uses only JDK HttpClient — no additional dependencies.
 */
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http;
    private final String apiUrl;
    private String bearerToken;

    public ApiClient() {
        this.apiUrl = AppConfig.API_URL;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    /**
     * Registers a new user and stores the returned JWT for subsequent calls.
     * Returns the displayName of the created user.
     */
    public String register(String login, String displayName, String password) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("login", login);
        body.put("displayName", displayName);
        body.put("password", password);

        JsonNode response = post("/api/users/register", body.toString(), false);
        this.bearerToken = response.path("token").asText();
        return response.path("displayName").asText();
    }

    /**
     * Logs in an existing user and stores the returned JWT.
     * Returns the displayName from the response.
     */
    public String login(String login, String password) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("login", login);
        body.put("password", password);

        JsonNode response = post("/api/users/login", body.toString(), false);
        this.bearerToken = response.path("token").asText();
        return response.path("displayName").asText();
    }

    /** Returns the stored Bearer token (null if not yet logged in). */
    public String getBearerToken() {
        return bearerToken;
    }

    // ── Templates ────────────────────────────────────────────────────────────────

    /**
     * Creates a draft template with the given name.
     * Requires a prior login() or register() call.
     *
     * @return the generated template ID
     */
    public String createTemplate(String name) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("name", name);
        JsonNode response = post("/api/templates", body.toString(), true);
        return response.path("id").asText();
    }

    /**
     * Deletes a template by ID.
     * Silently swallows 404 — safe to call in @AfterEach even if creation failed.
     */
    public void deleteTemplate(String id) {
        if (id == null || id.isBlank()) return;
        try {
            delete("/api/templates/" + id);
        } catch (RuntimeException e) {
            if (!e.getMessage().contains("HTTP 404")) throw e;
        }
    }

    /**
     * Publishes a template by ID.
     */
    public void publishTemplate(String id) {
        post("/api/templates/" + id + "/publish", "{}", true);
    }

    /**
     * Returns the ID of the first template whose name matches exactly, or null.
     * Uses the first page of results (size=100) — sufficient for test isolation.
     */
    public String findTemplateIdByName(String name) {
        try {
            JsonNode page = get("/api/templates?size=100&page=0");
            JsonNode content = page.path("content");
            for (JsonNode item : content) {
                if (name.equals(item.path("name").asText())) {
                    return item.path("id").asText();
                }
            }
        } catch (Exception ignored) {
            // teardown is best-effort
        }
        return null;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private JsonNode get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Authorization", "Bearer " + bearerToken)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException(
                        "API GET " + path + " failed: HTTP " + response.statusCode()
                                + " — " + response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API GET " + path + " error", e);
        }
    }

    private void delete(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Authorization", "Bearer " + bearerToken)
                    .DELETE()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException(
                        "API DELETE " + path + " failed: HTTP " + response.statusCode()
                                + " — " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API DELETE " + path + " error", e);
        }
    }

    private JsonNode post(String path, String jsonBody, boolean withAuth) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10));

            if (withAuth && bearerToken != null) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException(
                        "API POST " + path + " failed: HTTP " + response.statusCode()
                                + " — " + response.body());
            }
            return MAPPER.readTree(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("API POST " + path + " error", e);
        }
    }
}
