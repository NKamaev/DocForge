package com.docforge.e2e.base;

/**
 * Centralised configuration resolved from system properties (set in build.gradle.kts
 * from environment variables).
 *
 * Environment variables:
 *   APP_BASE_URL  — frontend origin, default http://localhost:5173
 *   APP_API_URL   — backend origin, default http://localhost:8080
 *   HEADLESS      — "false" to show browser window during debugging, default "true"
 */
public final class AppConfig {

    private AppConfig() {}

    /** Frontend base URL (no trailing slash). */
    public static final String BASE_URL =
            System.getProperty("app.base-url", "http://localhost:5173");

    /** Backend API base URL (no trailing slash). */
    public static final String API_URL =
            System.getProperty("app.api-url", "http://localhost:8080");

    /** Whether to run browser in headless mode. */
    public static final boolean HEADLESS =
            !"false".equalsIgnoreCase(System.getProperty("headless", "true"));

    /**
     * Browser to use: "chrome" (default) or "firefox".
     * Set via env BROWSER=firefox when Chrome is unavailable.
     */
    public static final String BROWSER =
            System.getProperty("browser",
                    System.getenv().getOrDefault("BROWSER", "chrome"));

    /** Default wait timeout in seconds for WebDriverWait. */
    public static final long WAIT_TIMEOUT_SECONDS = 10L;
}
