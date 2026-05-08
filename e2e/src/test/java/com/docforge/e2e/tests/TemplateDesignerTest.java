package com.docforge.e2e.tests;

import com.docforge.e2e.base.BaseTest;
import com.docforge.e2e.pages.TemplateDesignerPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 — Template designer scenarios (TipTap editor + toolbar).
 *
 * Test data strategy:
 *   One shared user, registered in @BeforeAll via REST API.
 *   Each test creates a fresh template via API in @BeforeEach
 *   and deletes it in @AfterEach.
 *
 * Covered scenarios:
 *   TC-TD01 — Type text, save, reload → text persists
 *   TC-TD02 — Add a parameter via sidebar panel and insert it via toolbar
 *   TC-TD03 — Toolbar shows font family and size on a new empty paragraph (regression)
 *   TC-TD04 — Insert conditional block → label appears in editor
 *   TC-TD05 — [DISABLED] Publish button not yet exposed in the designer UI
 */
@DisplayName("Template Designer")
class TemplateDesignerTest extends BaseTest {

    private String testLogin;
    private String testPassword;

    private String templateId;

    // ── Test data setup ───────────────────────────────────────────────────────

    @BeforeAll
    void registerTestUser() {
        testLogin    = "e2e_td_" + uuid8();
        testPassword = "E2eTest#1";
        api.register(testLogin, "E2E Designer", testPassword);
    }

    @BeforeEach
    void createTemplateAndLogin() {
        clearSession();
        loginAs(testLogin, testPassword);
        templateId = api.createTemplate("E2E Designer Template " + uuid8());
    }

    @AfterEach
    void deleteTemplate() {
        api.deleteTemplate(templateId);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-TD01: Text typed in editor persists after save and page reload")
    void typeAndSave_thenReload_textIsPersisted() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — type text and save
        String text = "Договор № E2E-" + uuid8();
        page.typeInEditor(text);
        page.save();

        // when — reload the page
        page.reload();

        // then — the text is present in the editor after reload
        page.assertEditorContains(text);
    }

    @Test
    @DisplayName("TC-TD02: Add parameter via sidebar and insert it into editor body")
    void addParameter_thenInsertViaToolbar_badgeAppearsInEditor() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — add a TEXT parameter via the sidebar panel
        String code  = "client_" + uuid8();
        String label = "Имя клиента";

        page.openParameterTab()
            .clickAddParameter()
            .fillParamCode(code)
            .fillParamLabel(label)
            // type is TEXT by default — no need to change
            .confirmAddParameter();

        // when — type some text so there's a cursor position, then insert the parameter
        page.typeInEditor("Клиент: ");
        page.insertParameterFromToolbar(code);

        // then — parameter field badge with the given code is visible in the editor
        page.assertParameterFieldExists(code);
    }

    @Test
    @DisplayName("TC-TD03: Toolbar shows font family and size on a fresh paragraph (regression: empty toolbar bug)")
    void newParagraph_toolbarShowsDefaultFontAndSize() {
        // given — designer is open; default formatting is Times New Roman / 14
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — type some text, set an explicit font and size, then press Enter
        page.typeInEditor("Первый абзац");
        page.setFontFamily("Times New Roman");
        page.setFontSize("14");
        page.pressEnterInEditor();

        // then — toolbar still shows the document defaults (not empty)
        // Without the fix, the selects would show empty placeholders after Enter
        String fontLabel = page.getFontFamilyLabel();
        String sizeLabel = page.getFontSizeLabel();

        assertThat(fontLabel)
                .as("Font family select should fall back to document default on new paragraph")
                .isNotEmpty();
        assertThat(sizeLabel)
                .as("Font size select should fall back to document default on new paragraph")
                .isNotEmpty();
    }

    @Test
    @DisplayName("TC-TD04: Insert conditional block — label appears in editor")
    void insertConditionalBlock_blockLabelIsVisibleInEditor() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — insert a conditional block via the toolbar "Условие" button
        page.insertConditionalBlock();

        // then — a conditional block with the default label "Условный блок" is visible
        page.assertConditionalBlockExists();
    }

    @Test
    @Disabled("TC-TD05: Publish button is not yet exposed in the template designer UI. "
            + "Tracked via publishTemplate() API — add the test once the UI button is added.")
    @DisplayName("TC-TD05: Publish template — badge 'Опубликовано' appears in toolbar")
    void publishTemplate_badgeAppearsInToolbar() {
        // Not implemented: no publish button exists in the designer header.
        // The API endpoint /templates/{id}/publish is available (ApiClient.publishTemplate()),
        // but the designer does not expose a publish action in the current UI.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String uuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
