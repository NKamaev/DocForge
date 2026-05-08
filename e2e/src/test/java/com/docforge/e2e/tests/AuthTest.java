package com.docforge.e2e.tests;

import com.docforge.e2e.base.AppConfig;
import com.docforge.e2e.base.BaseTest;
import com.docforge.e2e.pages.LoginPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 — Authentication scenarios.
 *
 * Test data strategy:
 *   A unique test user is registered once via the REST API in @BeforeAll.
 *   Login/password are stored as instance fields and reused across tests.
 *   (No user delete endpoint exists, so we generate a UUID-based login
 *   to avoid collisions between test runs.)
 *
 * Covered scenarios:
 *   TC-A01 — Successful login → redirect, header shows displayName
 *   TC-A02 — Wrong password → error toast, stays on /login
 *   TC-A03 — Logout → redirect to /login, protected route requires re-auth
 */
@DisplayName("Authentication")
class AuthTest extends BaseTest {

    private String testLogin;
    private String testPassword;
    private String testDisplayName;

    // ── Test data setup ───────────────────────────────────────────────────────

    @BeforeAll
    void registerTestUser() {
        testLogin = "e2e_auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        testPassword = "E2eTest#1";
        testDisplayName = "E2E Auth User";

        // Register via REST API — no browser interaction needed
        api.register(testLogin, testDisplayName, testPassword);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-A01: Successful login redirects to app and shows displayName in header")
    void login_withValidCredentials_redirectsToAppAndShowsDisplayName() {
        // given — ensure clean unauthenticated state
        clearSession();
        LoginPage loginPage = new LoginPage(driver, wait).open();

        // when
        loginPage
                .fillLogin(testLogin)
                .fillPassword(testPassword)
                .submitLogin()
                .waitForAppLayout();

        // then — URL is no longer /login
        assertThat(driver.getCurrentUrl())
                .as("Should redirect away from /login after successful auth")
                .doesNotEndWith("/login");

        // then — header contains the user's displayName
        boolean displayNameVisible = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//header//*[normalize-space(text())='" + testDisplayName + "']")
        )) != null;
        assertThat(displayNameVisible)
                .as("Header should show displayName '%s'", testDisplayName)
                .isTrue();
    }

    @Test
    @DisplayName("TC-A02: Wrong password shows error toast and stays on /login")
    void login_withWrongPassword_showsErrorAndStaysOnLogin() {
        // given — ensure clean unauthenticated state (previous test may have logged in)
        clearSession();
        LoginPage loginPage = new LoginPage(driver, wait).open();

        // when
        loginPage
                .fillLogin(testLogin)
                .fillPassword("wrong-password-xyz")
                .submitLogin();

        // then — error toast appears
        String toastText = loginPage.waitForErrorToast();
        assertThat(toastText)
                .as("Should show error message for invalid credentials")
                .contains("Неверный логин или пароль");

        // then — still on /login
        assertThat(loginPage.isOnLoginPage())
                .as("Should remain on /login page after failed auth")
                .isTrue();
    }

    @Test
    @DisplayName("TC-A03: Logout redirects to /login and protects routes")
    void logout_afterLogin_redirectsToLoginAndProtectsRoutes() {
        // given — ensure clean state, then log in
        clearSession();
        loginAs(testLogin, testPassword);

        // when — click logout
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[.//span[text()='Выйти']]")
        )).click();

        // then — redirected to /login
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl())
                .as("Should redirect to /login after logout")
                .contains("/login");

        // then — protected route redirects back to /login
        driver.get(AppConfig.BASE_URL + "/templates");
        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.getCurrentUrl())
                .as("Accessing /templates without auth should redirect to /login")
                .contains("/login");
    }
}
