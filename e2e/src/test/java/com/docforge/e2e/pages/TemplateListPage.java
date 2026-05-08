package com.docforge.e2e.pages;

import com.docforge.e2e.base.AppConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page Object for /templates — the template list screen.
 *
 * HTML structure (Ant Design v5 Table):
 *   .ant-table-wrapper
 *     .ant-spin-container
 *       .ant-table
 *         .ant-table-tbody
 *           .ant-table-row  (one per template)
 *
 * Action buttons per row:
 *   Button[link] "Открыть" — navigates to editor
 *   Button[link, danger] "Удалить" → Popconfirm → "Удалить" (OK)
 *
 * Create flow:
 *   Button[primary] "Создать шаблон" → Modal "Создать шаблон"
 *     Input[placeholder='Договор оказания услуг'] → name field
 *     Modal footer .ant-btn-primary → "Создать"
 */
public class TemplateListPage {

    // ── Locators ──────────────────────────────────────────────────────────────

    private static final By TABLE_BODY =
            By.cssSelector(".ant-table-tbody");

    private static final By TABLE_SPINNER =
            By.cssSelector(".ant-table-wrapper .ant-spin-spinning");

    private static final By CREATE_BUTTON =
            By.xpath("//button[.//span[normalize-space(text())='Создать шаблон']]");

    /** Modal name input (inside CreateTemplateModal). */
    private static final By MODAL_NAME_INPUT =
            By.cssSelector(".ant-modal-body input[placeholder='Договор оказания услуг']");

    /** "Создать" confirm button inside the modal footer. */
    private static final By MODAL_CONFIRM_BUTTON =
            By.cssSelector(".ant-modal-footer .ant-btn-primary");

    /** "Удалить" confirm button inside the Ant Design Popconfirm tooltip. */
    private static final By POPCONFIRM_OK =
            By.cssSelector(".ant-popconfirm-buttons .ant-btn-primary");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final WebDriver driver;
    private final WebDriverWait wait;

    public TemplateListPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Navigates to /templates and waits for the table to render. */
    public TemplateListPage open() {
        driver.get(AppConfig.BASE_URL + "/templates");
        waitForTableLoaded();
        return this;
    }

    /** Waits until the Ant Design Table loading spinner is gone and tbody is visible. */
    public TemplateListPage waitForTableLoaded() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(TABLE_BODY));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(TABLE_SPINNER));
        return this;
    }

    // ── Create flow ───────────────────────────────────────────────────────────

    /** Opens the "Создать шаблон" modal. */
    public TemplateListPage clickCreateTemplate() {
        wait.until(ExpectedConditions.elementToBeClickable(CREATE_BUTTON)).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(MODAL_NAME_INPUT));
        return this;
    }

    /** Types the template name into the modal name input. */
    public TemplateListPage fillTemplateName(String name) {
        WebElement input = wait.until(
                ExpectedConditions.visibilityOfElementLocated(MODAL_NAME_INPUT));
        input.clear();
        input.sendKeys(name);
        return this;
    }

    /**
     * Clicks the "Создать" button in the modal footer.
     * Waits for the modal to close before returning.
     */
    public TemplateListPage confirmCreateTemplate() {
        wait.until(ExpectedConditions.elementToBeClickable(MODAL_CONFIRM_BUTTON)).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(MODAL_CONFIRM_BUTTON));
        waitForTableLoaded();
        return this;
    }

    // ── Row interactions ──────────────────────────────────────────────────────

    /**
     * Waits until a row with the given template name appears in the table.
     * Useful after creating a template via UI.
     */
    public TemplateListPage waitForTemplateInList(String name) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(rowByName(name)));
        return this;
    }

    /**
     * Clicks the "Открыть" link for the row matching {@code name}
     * and returns a TemplateDesignerPage for further interaction.
     */
    public TemplateDesignerPage openTemplate(String name) {
        By openBtn = By.xpath(
                "//tr[.//td[normalize-space(text())='" + name + "']]"
                        + "//button[.//span[normalize-space(text())='Открыть']]");
        wait.until(ExpectedConditions.elementToBeClickable(openBtn)).click();
        return new TemplateDesignerPage(driver, wait);
    }

    /**
     * Clicks "Удалить" in the row matching {@code name}, then confirms via Popconfirm.
     * Waits until the row disappears from the table.
     */
    public TemplateListPage deleteTemplateViaUI(String name) {
        By deleteBtn = By.xpath(
                "//tr[.//td[normalize-space(text())='" + name + "']]"
                        + "//button[.//span[normalize-space(text())='Удалить']]");
        wait.until(ExpectedConditions.elementToBeClickable(deleteBtn)).click();

        // Confirm in Popconfirm
        wait.until(ExpectedConditions.elementToBeClickable(POPCONFIRM_OK)).click();

        // Row should disappear
        wait.until(ExpectedConditions.invisibilityOfElementLocated(rowByName(name)));
        return this;
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /** Asserts that a row with the given name is visible in the table. */
    public void assertTemplateExists(String name) {
        assertThat(driver.findElements(rowByName(name)))
                .as("Template row with name '%s' should be visible", name)
                .isNotEmpty();
    }

    /** Asserts that no row with the given name is present in the table. */
    public void assertTemplateAbsent(String name) {
        assertThat(driver.findElements(rowByName(name)))
                .as("Template row with name '%s' should not be visible", name)
                .isEmpty();
    }

    /** Asserts that the "Открыть" and "Удалить" buttons exist in the row for {@code name}. */
    public void assertRowHasActionButtons(String name) {
        By openBtn = By.xpath(
                "//tr[.//td[normalize-space(text())='" + name + "']]"
                        + "//button[.//span[normalize-space(text())='Открыть']]");
        By deleteBtn = By.xpath(
                "//tr[.//td[normalize-space(text())='" + name + "']]"
                        + "//button[.//span[normalize-space(text())='Удалить']]");

        assertThat(driver.findElements(openBtn))
                .as("'Открыть' button should be present for '%s'", name)
                .isNotEmpty();
        assertThat(driver.findElements(deleteBtn))
                .as("'Удалить' button should be present for '%s'", name)
                .isNotEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static By rowByName(String name) {
        return By.xpath("//tr[.//td[normalize-space(text())='" + name + "']]");
    }
}
