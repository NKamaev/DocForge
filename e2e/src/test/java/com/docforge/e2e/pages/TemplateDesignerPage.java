package com.docforge.e2e.pages;

import com.docforge.e2e.base.AppConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page Object for /templates/{id}/edit — the TipTap-based template designer.
 *
 * Layout (TemplateDesigner.tsx):
 *   Header  — title + action buttons (Сохранить, Предпросмотр, …)
 *   Body    — editor column (70%) + sidebar column with Tabs (30%)
 *     Editor column:
 *       EditorToolbar  — font/size/spacing Selects + formatting buttons
 *       .tiptap-page
 *         div.ProseMirror[contenteditable='true']  ← text editing area
 *     Sidebar:
 *       Tabs: "Параметры" | "Опросный лист" | "Скрипты"
 *         ParameterPanel — "Добавить" button + parameter list
 *
 * Key node types in editor:
 *   span[data-type='parameter-field'][data-parameter-code='…']  — parameter badge
 *   [contenteditable='false'] "⚡ Условный блок"                — conditional block header
 *   div[data-type='page-break']                                 — page break
 *   strong, em, u                                               — inline formatting
 *   ul li, ol li                                                — lists
 *   table                                                       — table node
 */
public class TemplateDesignerPage {

    // ── Editor ────────────────────────────────────────────────────────────────

    private static final By EDITOR =
            By.cssSelector("div.ProseMirror[contenteditable='true']");

    // ── Header actions ────────────────────────────────────────────────────────

    private static final By SAVE_BUTTON =
            By.xpath("//button[contains(@class,'ant-btn-primary') "
                    + "and .//span[normalize-space(text())='Сохранить']]");

    private static final By SAVE_NOTIFICATION =
            By.xpath("//*[contains(@class,'ant-notification-notice-message')"
                    + " and normalize-space(text())='Шаблон сохранён']");

    // ── Toolbar — font selects ─────────────────────────────────────────────────
    // Identified by the inline width set on the Ant Design Select component.

    private static final By FONT_FAMILY_SELECTOR =
            By.cssSelector(".ant-select[style*='width: 140px'] .ant-select-selector");

    private static final By FONT_SIZE_SELECTOR =
            By.cssSelector(".ant-select[style*='width: 68px'] .ant-select-selector");

    private static final By FONT_FAMILY_VALUE =
            By.cssSelector(".ant-select[style*='width: 140px'] .ant-select-selection-item");

    private static final By FONT_SIZE_VALUE =
            By.cssSelector(".ant-select[style*='width: 68px'] .ant-select-selection-item");

    // ── Toolbar — template-specific buttons ───────────────────────────────────

    private static final By PARAMETER_DROPDOWN_BTN =
            By.xpath("//button[contains(@class,'ant-btn-dashed')"
                    + " and .//span[normalize-space(text())='Параметр']]");

    private static final By CONDITIONAL_BLOCK_BTN =
            By.xpath("//button[contains(@class,'ant-btn-dashed')"
                    + " and .//span[normalize-space(text())='Условие']]");

    // ── Sidebar — parameter panel ─────────────────────────────────────────────

    private static final By PARAMETERS_TAB =
            By.xpath("//div[contains(@class,'ant-tabs-tab')]"
                    + "[.//*[normalize-space(text())='Параметры']]");

    private static final By ADD_PARAMETER_BTN =
            By.xpath("//button[contains(@class,'ant-btn-primary')"
                    + " and .//span[normalize-space(text())='Добавить']]");

    // ── Parameter modal ───────────────────────────────────────────────────────

    private static final By PARAM_CODE_INPUT =
            By.cssSelector(".ant-modal-body input[placeholder='client_name']");

    private static final By PARAM_LABEL_INPUT =
            By.cssSelector(".ant-modal-body input[placeholder='ФИО клиента']");

    private static final By PARAM_TYPE_SELECTOR =
            By.cssSelector(".ant-modal-body .ant-form-item:nth-of-type(3) .ant-select-selector");

    private static final By MODAL_SAVE_BTN =
            By.cssSelector(".ant-modal-footer .ant-btn-primary");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final Actions actions;

    public TemplateDesignerPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
        this.actions = new Actions(driver);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Navigates to the designer for the given template ID and waits for the editor. */
    public TemplateDesignerPage open(String templateId) {
        driver.get(AppConfig.BASE_URL + "/templates/" + templateId + "/edit");
        waitForEditorReady();
        return this;
    }

    /**
     * Waits until the ProseMirror editor is visible and interactable.
     * Also waits for the template data to load (Spin disappears).
     */
    public TemplateDesignerPage waitForEditorReady() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(EDITOR));
        // Wait a moment for the editor to load template content via useEffect
        wait.until(ExpectedConditions.elementToBeClickable(EDITOR));
        return this;
    }

    // ── Editor interactions ───────────────────────────────────────────────────

    /** Focuses the editor and types the given text. */
    public TemplateDesignerPage typeInEditor(String text) {
        WebElement editor = wait.until(ExpectedConditions.elementToBeClickable(EDITOR));
        editor.click();
        actions.sendKeys(text).perform();
        return this;
    }

    /** Presses Enter in the focused editor (creates a new paragraph). */
    public TemplateDesignerPage pressEnterInEditor() {
        WebElement editor = wait.until(ExpectedConditions.elementToBeClickable(EDITOR));
        editor.click();
        actions.sendKeys(Keys.ENTER).perform();
        return this;
    }

    /** Selects all text in the editor (Ctrl+A). */
    public TemplateDesignerPage selectAllInEditor() {
        WebElement editor = wait.until(ExpectedConditions.elementToBeClickable(EDITOR));
        editor.click();
        actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
        return this;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /** Saves via Ctrl+S and waits for the success notification. */
    public TemplateDesignerPage save() {
        actions.keyDown(Keys.CONTROL).sendKeys("s").keyUp(Keys.CONTROL).perform();
        waitForSaveNotification();
        return this;
    }

    /** Waits for the "Шаблон сохранён" notification to appear. */
    public TemplateDesignerPage waitForSaveNotification() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(SAVE_NOTIFICATION));
        return this;
    }

    /** Reloads the page and waits for the editor to be ready again. */
    public TemplateDesignerPage reload() {
        driver.navigate().refresh();
        waitForEditorReady();
        return this;
    }

    // ── Editor state reads ────────────────────────────────────────────────────

    /** Returns the plain text content of the editor. */
    public String getEditorText() {
        WebElement editor = wait.until(ExpectedConditions.visibilityOfElementLocated(EDITOR));
        return editor.getText();
    }

    /**
     * Returns the label shown in the font family Select (e.g. "Times New Roman"),
     * or empty string if the placeholder is shown.
     */
    public String getFontFamilyLabel() {
        var items = driver.findElements(FONT_FAMILY_VALUE);
        return items.isEmpty() ? "" : items.get(0).getText();
    }

    /**
     * Returns the label shown in the font size Select (e.g. "14"),
     * or empty string if the placeholder is shown.
     */
    public String getFontSizeLabel() {
        var items = driver.findElements(FONT_SIZE_VALUE);
        return items.isEmpty() ? "" : items.get(0).getText();
    }

    // ── Toolbar — font ────────────────────────────────────────────────────────

    /**
     * Selects a font family by its display label (e.g. "Times New Roman").
     * Opens the dropdown and clicks the matching option.
     */
    public TemplateDesignerPage setFontFamily(String label) {
        wait.until(ExpectedConditions.elementToBeClickable(FONT_FAMILY_SELECTOR)).click();
        clickDropdownOption(label);
        return this;
    }

    /**
     * Selects a font size by its display value (e.g. "14").
     */
    public TemplateDesignerPage setFontSize(String value) {
        wait.until(ExpectedConditions.elementToBeClickable(FONT_SIZE_SELECTOR)).click();
        clickDropdownOption(value);
        return this;
    }

    // ── Toolbar — formatting buttons (via keyboard shortcuts) ─────────────────

    /** Toggles Bold on the current selection via Ctrl+B. */
    public TemplateDesignerPage toggleBold() {
        actions.keyDown(Keys.CONTROL).sendKeys("b").keyUp(Keys.CONTROL).perform();
        return this;
    }

    /** Toggles Italic on the current selection via Ctrl+I. */
    public TemplateDesignerPage toggleItalic() {
        actions.keyDown(Keys.CONTROL).sendKeys("i").keyUp(Keys.CONTROL).perform();
        return this;
    }

    /**
     * Clicks the alignment button identified by the Ant Design icon aria-label.
     *
     * @param iconAriaLabel e.g. "align-center", "align-left", "align-right"
     */
    public TemplateDesignerPage clickAlignButton(String iconAriaLabel) {
        By btn = By.xpath(
                "//button[.//*[@aria-label='" + iconAriaLabel + "']]");
        wait.until(ExpectedConditions.elementToBeClickable(btn)).click();
        return this;
    }

    /**
     * Clicks the bullet list toolbar button (UnorderedListOutlined icon).
     */
    public TemplateDesignerPage clickBulletList() {
        By btn = By.xpath("//button[.//*[@aria-label='unordered-list']]");
        wait.until(ExpectedConditions.elementToBeClickable(btn)).click();
        return this;
    }

    /**
     * Clicks the ordered list toolbar button (OrderedListOutlined icon).
     */
    public TemplateDesignerPage clickOrderedList() {
        By btn = By.xpath("//button[.//*[@aria-label='ordered-list']]");
        wait.until(ExpectedConditions.elementToBeClickable(btn)).click();
        return this;
    }

    /**
     * Clicks the "Вставить таблицу" toolbar button (TableOutlined icon).
     */
    public TemplateDesignerPage clickInsertTable() {
        By btn = By.xpath("//button[.//*[@aria-label='table']]");
        wait.until(ExpectedConditions.elementToBeClickable(btn)).click();
        return this;
    }

    /**
     * Inserts a page break via Ctrl+Enter keyboard shortcut
     * (PageBreakNode keyboard shortcut: Mod-Enter).
     */
    public TemplateDesignerPage insertPageBreak() {
        focusEditor();
        actions.keyDown(Keys.CONTROL).sendKeys(Keys.ENTER).keyUp(Keys.CONTROL).perform();
        return this;
    }

    // ── Toolbar — template-specific ───────────────────────────────────────────

    /**
     * Inserts a conditional block into the editor by clicking the "Условие" toolbar button.
     */
    public TemplateDesignerPage insertConditionalBlock() {
        wait.until(ExpectedConditions.elementToBeClickable(CONDITIONAL_BLOCK_BTN)).click();
        return this;
    }

    /**
     * Opens the "Параметр" dropdown in the toolbar and clicks the option matching
     * the given parameter code (partial text match on code text).
     */
    public TemplateDesignerPage insertParameterFromToolbar(String paramCode) {
        wait.until(ExpectedConditions.elementToBeClickable(PARAMETER_DROPDOWN_BTN)).click();
        By option = By.xpath(
                "//li[contains(@class,'ant-dropdown-menu-item')"
                        + " and .//*[normalize-space(text())='" + paramCode + "']]");
        wait.until(ExpectedConditions.elementToBeClickable(option)).click();
        return this;
    }

    // ── Sidebar — parameter management ───────────────────────────────────────

    /** Clicks the "Параметры" tab in the sidebar (already active by default). */
    public TemplateDesignerPage openParameterTab() {
        wait.until(ExpectedConditions.elementToBeClickable(PARAMETERS_TAB)).click();
        return this;
    }

    /**
     * Opens the "Новый параметр" modal via the "Добавить" button in the parameter panel.
     */
    public TemplateDesignerPage clickAddParameter() {
        wait.until(ExpectedConditions.elementToBeClickable(ADD_PARAMETER_BTN)).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(PARAM_CODE_INPUT));
        return this;
    }

    /** Fills the code field in the parameter modal. */
    public TemplateDesignerPage fillParamCode(String code) {
        WebElement input = wait.until(
                ExpectedConditions.visibilityOfElementLocated(PARAM_CODE_INPUT));
        input.clear();
        input.sendKeys(code);
        return this;
    }

    /** Fills the label/name field in the parameter modal. */
    public TemplateDesignerPage fillParamLabel(String label) {
        WebElement input = wait.until(
                ExpectedConditions.visibilityOfElementLocated(PARAM_LABEL_INPUT));
        input.clear();
        input.sendKeys(label);
        return this;
    }

    /**
     * Selects the parameter type in the modal.
     *
     * @param typeLabel display label as shown in the select, e.g. "Текст", "Дата", "Список"
     */
    public TemplateDesignerPage selectParamType(String typeLabel) {
        wait.until(ExpectedConditions.elementToBeClickable(PARAM_TYPE_SELECTOR)).click();
        clickDropdownOption(typeLabel);
        return this;
    }

    /**
     * Clicks "Сохранить" in the parameter modal footer.
     * Waits for the modal to close.
     */
    public TemplateDesignerPage confirmAddParameter() {
        wait.until(ExpectedConditions.elementToBeClickable(MODAL_SAVE_BTN)).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(PARAM_CODE_INPUT));
        return this;
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /** Asserts that the editor text contains the given substring. */
    public void assertEditorContains(String text) {
        String content = getEditorText();
        assertThat(content)
                .as("Editor should contain '%s'", text)
                .contains(text);
    }

    /** Asserts that a parameter field badge with the given code exists in the editor. */
    public void assertParameterFieldExists(String code) {
        By badge = By.cssSelector("span[data-parameter-code='" + code + "']");
        wait.until(ExpectedConditions.visibilityOfElementLocated(badge));
    }

    /**
     * Asserts that a conditional block with the default label "Условный блок" is present
     * in the editor DOM.
     */
    public void assertConditionalBlockExists() {
        By block = By.xpath(
                "//div[contains(@class,'ProseMirror')]"
                        + "//*[@contenteditable='false' and contains(.,'Условный блок')]");
        wait.until(ExpectedConditions.visibilityOfElementLocated(block));
    }

    /** Asserts that the editor contains a {@code <strong>} element (bold mark active). */
    public void assertBoldMarkExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror strong")))
                .as("Editor should contain a <strong> (bold) element")
                .isNotEmpty();
    }

    /** Asserts that the editor contains an {@code <em>} element (italic mark active). */
    public void assertItalicMarkExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror em")))
                .as("Editor should contain an <em> (italic) element")
                .isNotEmpty();
    }

    /** Asserts that the editor contains a {@code <ul>} list. */
    public void assertBulletListExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror ul li")))
                .as("Editor should contain a bullet list")
                .isNotEmpty();
    }

    /** Asserts that the editor contains an {@code <ol>} list. */
    public void assertOrderedListExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror ol li")))
                .as("Editor should contain an ordered list")
                .isNotEmpty();
    }

    /** Asserts that the editor contains a table. */
    public void assertTableExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror table")))
                .as("Editor should contain a table")
                .isNotEmpty();
    }

    /** Asserts that the editor contains a page break node. */
    public void assertPageBreakExists() {
        assertThat(driver.findElements(By.cssSelector(".ProseMirror .page-break-node")))
                .as("Editor should contain a page-break node")
                .isNotEmpty();
    }

    /**
     * Checks that a paragraph in the editor has the given text alignment applied.
     * Looks for a block-level element with style containing "text-align: {align}".
     */
    public void assertTextAlignInEditor(String align) {
        By aligned = By.cssSelector(
                ".ProseMirror [style*='text-align: " + align + "'],"
                        + ".ProseMirror [style*='text-align:" + align + "']");
        assertThat(driver.findElements(aligned))
                .as("Editor should contain an element aligned '%s'", align)
                .isNotEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void focusEditor() {
        wait.until(ExpectedConditions.elementToBeClickable(EDITOR)).click();
    }

    /**
     * Clicks a visible option inside an open Ant Design Select dropdown portal.
     * Options render as {@code .ant-select-item-option-content} elements in a portal.
     */
    private void clickDropdownOption(String optionText) {
        By option = By.xpath(
                "//div[contains(@class,'ant-select-dropdown') and not(contains(@style,'display: none'))]"
                        + "//div[contains(@class,'ant-select-item-option-content')"
                        + " and normalize-space(text())='" + optionText + "']");
        wait.until(ExpectedConditions.elementToBeClickable(option)).click();
    }

    /**
     * Clicks a cell in the first table found in the editor and types text.
     */
    public TemplateDesignerPage typeInFirstTableCell(String text) {
        By firstCell = By.cssSelector(".ProseMirror table td");
        WebElement cell = wait.until(ExpectedConditions.elementToBeClickable(firstCell));
        cell.click();
        actions.sendKeys(text).perform();
        return this;
    }

    /**
     * Returns the text content of the first table cell in the editor.
     */
    public String getFirstTableCellText() {
        WebElement cell = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector(".ProseMirror table td")));
        return (String) ((JavascriptExecutor) driver)
                .executeScript("return arguments[0].innerText;", cell);
    }
}
