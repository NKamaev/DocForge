package com.docforge.e2e.base;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.docforge.e2e.pages.LoginPage;

import java.time.Duration;

/**
 * Base class for all Selenium E2E tests.
 *
 * Lifecycle (PER_CLASS):
 *   @BeforeAll — starts ChromeDriver once per test class.
 *   @AfterAll  — quits ChromeDriver after all tests in the class.
 *
 * Subclasses should use @BeforeEach to log in and set up test data,
 * and @AfterEach to clean up via ApiClient.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTest {

    protected WebDriver driver;
    protected WebDriverWait wait;

    /** Fresh ApiClient per test class — subclasses may use it for data setup. */
    protected final ApiClient api = new ApiClient();

    @BeforeAll
    void setUpDriver() {
        driver = "firefox".equalsIgnoreCase(AppConfig.BROWSER)
                ? createFirefoxDriver()
                : createChromeDriver();

        driver.manage().window().setSize(
                new org.openqa.selenium.Dimension(1280, 900));
        driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(30))
                .implicitlyWait(Duration.ofMillis(0)); // explicit waits only

        wait = new WebDriverWait(driver, Duration.ofSeconds(AppConfig.WAIT_TIMEOUT_SECONDS));
    }

    private WebDriver createChromeDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        if (AppConfig.HEADLESS) {
            options.addArguments(
                    "--headless=new",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--window-size=1280,900"
            );
        }
        options.addArguments("--disable-save-password-bubble", "--disable-notifications");
        return new ChromeDriver(options);
    }

    private WebDriver createFirefoxDriver() {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        if (AppConfig.HEADLESS) {
            options.addArguments("--headless");
        }
        return new FirefoxDriver(options);
    }

    @AfterAll
    void tearDownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    /**
     * Navigates to the login page and performs a login with the given credentials.
     * Blocks until the app layout is visible (auth redirect completed).
     */
    protected void loginAs(String login, String password) {
        new LoginPage(driver, wait)
                .open()
                .fillLogin(login)
                .fillPassword(password)
                .submitLogin()
                .waitForAppLayout();
    }

    /**
     * Clears browser cookies and localStorage, then navigates to /login.
     * Call in @BeforeEach if you need a fresh unauthenticated state.
     */
    protected void clearSession() {
        driver.get(AppConfig.BASE_URL + "/login");
        driver.manage().deleteAllCookies();
        // Clear localStorage via JS
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("localStorage.clear(); sessionStorage.clear();");
        } catch (Exception ignored) {
            // page may not have JS context yet
        }
        driver.navigate().refresh();
    }
}
