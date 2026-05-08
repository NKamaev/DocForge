package com.docforge.e2e.tests;

import com.docforge.e2e.base.AppConfig;
import com.docforge.e2e.base.BaseTest;
import com.docforge.e2e.pages.TemplateListPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 — Template list page scenarios.
 *
 * Test data strategy:
 *   A shared test user is registered once in @BeforeAll via the REST API.
 *   Individual tests create/delete templates via the REST API where possible;
 *   TC-TL02 creates a template through the UI (to exercise the creation flow).
 *   @AfterEach cleans up any templates created during the test.
 *
 * Covered scenarios:
 *   TC-TL01 — Template list is displayed with action buttons
 *   TC-TL02 — Creating a template via UI redirects to the list and adds the row
 *   TC-TL03 — Deleting a template via UI removes it from the list
 */
@DisplayName("Template List")
class TemplateListTest extends BaseTest {

    private String testLogin;
    private String testPassword;

    /** ID of the template created in @BeforeEach via API (null for TC-TL02). */
    private String apiCreatedTemplateId;

    /** Name used for the template created in TC-TL02 (deleted in @AfterEach via API). */
    private String uiCreatedTemplateName;

    // ── Test data setup ───────────────────────────────────────────────────────

    @BeforeAll
    void registerTestUser() {
        testLogin    = "e2e_tl_" + uuid8();
        testPassword = "E2eTest#1";
        api.register(testLogin, "E2E TemplateList", testPassword);
    }

    @BeforeEach
    void loginUser() {
        clearSession();
        loginAs(testLogin, testPassword);
        apiCreatedTemplateId = null;
        uiCreatedTemplateName = null;
    }

    @AfterEach
    void cleanUp() {
        if (apiCreatedTemplateId != null) {
            api.deleteTemplate(apiCreatedTemplateId);
        }
        if (uiCreatedTemplateName != null) {
            String id = api.findTemplateIdByName(uiCreatedTemplateName);
            api.deleteTemplate(id);
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-TL01: Template list shows existing template with action buttons")
    void templateList_withExistingTemplate_showsRowWithActionButtons() {
        // given — a template exists in the database
        String name = "E2E Template " + uuid8();
        apiCreatedTemplateId = api.createTemplate(name);

        // when — open the /templates page
        TemplateListPage listPage = new TemplateListPage(driver, wait).open();

        // then — the template row is visible
        listPage.assertTemplateExists(name);

        // then — the row contains "Открыть" and "Удалить" buttons
        listPage.assertRowHasActionButtons(name);
    }

    @Test
    @DisplayName("TC-TL02: Creating a template via UI modal adds it to the list")
    void createTemplate_viaModal_addsRowToList() {
        // given — user is on the template list page with no pre-created template
        uiCreatedTemplateName = "E2E New Template " + uuid8();
        TemplateListPage listPage = new TemplateListPage(driver, wait).open();

        // when — click "Создать шаблон", fill the name, confirm
        listPage
                .clickCreateTemplate()
                .fillTemplateName(uiCreatedTemplateName)
                .confirmCreateTemplate();

        // then — the new template row appears in the list
        listPage.waitForTemplateInList(uiCreatedTemplateName);
        listPage.assertTemplateExists(uiCreatedTemplateName);

        // then — the current URL must still be the template list (no redirect to editor)
        assertThat(driver.getCurrentUrl())
                .as("After creation, should stay on /templates")
                .contains("/templates");
    }

    @Test
    @DisplayName("TC-TL03: Deleting a template via UI removes it from the list")
    void deleteTemplate_viaPopconfirm_removesRowFromList() {
        // given — a template exists
        String name = "E2E Delete Me " + uuid8();
        apiCreatedTemplateId = api.createTemplate(name);

        // when — open the list and delete via the "Удалить" → Popconfirm flow
        TemplateListPage listPage = new TemplateListPage(driver, wait).open();
        listPage.deleteTemplateViaUI(name);

        // then — the row is no longer present
        listPage.assertTemplateAbsent(name);

        // after successful UI delete the server record is gone, no API cleanup needed
        apiCreatedTemplateId = null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String uuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
