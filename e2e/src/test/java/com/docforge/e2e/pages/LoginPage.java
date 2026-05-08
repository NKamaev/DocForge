package com.docforge.e2e.pages;

import com.docforge.e2e.base.AppConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for /login.
 *
 * The page has two Ant Design Tabs: "Вход" (login) and "Регистрация" (register).
 * By default the "Вход" tab is active.
 *
 * HTML structure (Ant Design v5):
 *   .ant-card
 *     .ant-tabs
 *       .ant-tabs-nav  → tab buttons
 *       .ant-tabs-content-holder
 *         .ant-tabs-tabpane.ant-tabs-tabpane-active  ← active tab
 *           form
 *             input[placeholder='Логин']
 *             input[placeholder='Пароль']
 *             button[type='submit']  ← "Войти"
 */
public class LoginPage {

    // ── Locators ─────────────────────────────────────────────────────────────

    /** Login input — scoped to active tab pane to avoid matching the register tab. */
    private static final By LOGIN_INPUT =
            By.cssSelector(".ant-tabs-tabpane-active input[placeholder='Логин']");

    /** Password input — scoped to active tab pane. */
    private static final By PASSWORD_INPUT =
            By.cssSelector(".ant-tabs-tabpane-active input[placeholder='Пароль']");

    /** "Войти" submit button — scoped to active tab pane. */
    private static final By SUBMIT_BTN =
            By.cssSelector(".ant-tabs-tabpane-active button[type='submit']");

    /**
     * Ant Design message error toast — matched by text content.
     * Ant Design v5 renders the toast as a portal into document.body.
     * Finding by exact text is class-name-agnostic and works across AntD versions.
     */
    private static final By ERROR_TOAST =
            By.xpath("//*[normalize-space(.)='Неверный логин или пароль']");

    /**
     * App layout header — present only after successful login.
     * Used by waitForAppLayout().
     */
    private static final By APP_HEADER =
            By.cssSelector(".ant-layout-header");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final WebDriver driver;
    private final WebDriverWait wait;

    public LoginPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Navigates to /login and waits until the login input is visible. */
    public LoginPage open() {
        driver.get(AppConfig.BASE_URL + "/login");
        wait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_INPUT));
        return this;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    public LoginPage fillLogin(String login) {
        WebElement input = wait.until(
                ExpectedConditions.visibilityOfElementLocated(LOGIN_INPUT));
        input.clear();
        input.sendKeys(login);
        return this;
    }

    public LoginPage fillPassword(String password) {
        WebElement input = wait.until(
                ExpectedConditions.visibilityOfElementLocated(PASSWORD_INPUT));
        input.clear();
        input.sendKeys(password);
        return this;
    }

    /**
     * Clicks the "Войти" button and returns this LoginPage.
     * Use {@link #waitForAppLayout()} afterwards to assert success,
     * or {@link #waitForErrorToast()} to assert failure.
     */
    public LoginPage submitLogin() {
        wait.until(ExpectedConditions.elementToBeClickable(SUBMIT_BTN)).click();
        return this;
    }

    // ── Assertions / Waits ────────────────────────────────────────────────────

    /**
     * Waits until the app layout header is rendered (indicates successful login redirect).
     */
    public LoginPage waitForAppLayout() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(APP_HEADER));
        return this;
    }

    /**
     * Waits for the Ant Design error toast to appear and returns its text.
     * Matched by the visible span containing the error text inside the portal.
     */
    public String waitForErrorToast() {
        WebElement toast = wait.until(
                ExpectedConditions.visibilityOfElementLocated(ERROR_TOAST));
        return toast.getText();
    }

    /**
     * Returns the current browser URL.
     */
    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /**
     * Returns true if the URL ends with "/login".
     */
    public boolean isOnLoginPage() {
        return driver.getCurrentUrl().endsWith("/login");
    }
}
