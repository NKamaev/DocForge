package com.docforge.e2e.tests;

import com.docforge.e2e.base.BaseTest;
import com.docforge.e2e.pages.TemplateDesignerPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — TipTap editor formatting scenarios.
 *
 * Each test opens a fresh template, applies formatting in the editor,
 * and verifies the resulting DOM structure inside {@code .ProseMirror}.
 *
 * Covered scenarios:
 *   TC-FMT01 — Bold and italic inline marks
 *   TC-FMT02 — Text alignment (center)
 *   TC-FMT03 — Bullet and ordered lists
 *   TC-FMT04 — Table: insert and type in a cell
 *   TC-FMT05 — Page break node
 */
@DisplayName("Formatting")
class FormattingTest extends BaseTest {

    private String testLogin;
    private String testPassword;
    private String templateId;

    // ── Test data setup ───────────────────────────────────────────────────────

    @BeforeAll
    void registerTestUser() {
        testLogin    = "e2e_fmt_" + uuid8();
        testPassword = "E2eTest#1";
        api.register(testLogin, "E2E Formatting", testPassword);
    }

    @BeforeEach
    void createFreshTemplate() {
        clearSession();
        loginAs(testLogin, testPassword);
        templateId = api.createTemplate("E2E Formatting " + uuid8());
    }

    @AfterEach
    void deleteTemplate() {
        api.deleteTemplate(templateId);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-FMT01: Bold and italic marks applied to selected text")
    void applyBoldAndItalic_selectedText_marksAppearedInDom() {
        // given — designer is open with some text
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);
        page.typeInEditor("Форматирование");

        // when — select all and apply bold
        page.selectAllInEditor();
        page.toggleBold();

        // then — <strong> element is present in the editor
        page.assertBoldMarkExists();

        // when — apply italic on the same selection
        page.selectAllInEditor();
        page.toggleItalic();

        // then — <em> element is present in the editor
        page.assertItalicMarkExists();
    }

    @Test
    @DisplayName("TC-FMT02: Center alignment applied to a paragraph")
    void applyAlignCenter_paragraph_styleIsCenter() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — type text, then click the "По центру" alignment button
        page.typeInEditor("Текст по центру");
        page.clickAlignButton("align-center");

        // then — the paragraph has text-align: center in its style
        page.assertTextAlignInEditor("center");
    }

    @Test
    @DisplayName("TC-FMT03: Bullet list and ordered list inserted from toolbar")
    void insertLists_toolbarButtons_listNodesAppearInEditor() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — click the bullet list button and type an item
        page.typeInEditor("Первая строка");
        page.clickBulletList();
        page.typeInEditor("Элемент списка");

        // then — a <ul> with an <li> is present
        page.assertBulletListExists();

        // when — on a new paragraph, click the ordered list button
        page.pressEnterInEditor();
        page.pressEnterInEditor(); // exit list
        page.typeInEditor("Нумерованный");
        page.clickOrderedList();

        // then — a <ol> with an <li> is present
        page.assertOrderedListExists();
    }

    @Test
    @DisplayName("TC-FMT04: Table inserted from toolbar; text can be typed in a cell")
    void insertTable_typeInCell_cellContainsText() {
        // given — designer is open
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);

        // when — click the "Вставить таблицу" toolbar button
        page.typeInEditor("Перед таблицей");
        page.pressEnterInEditor();
        page.clickInsertTable();

        // then — a <table> is present in the editor
        page.assertTableExists();

        // when — click in the first cell and type text
        String cellText = "Ячейка " + uuid8();
        page.typeInFirstTableCell(cellText);

        // then — cell contains the typed text
        String actual = page.getFirstTableCellText();
        assertThat(actual)
                .as("First table cell should contain the typed text")
                .contains(cellText);
    }

    @Test
    @DisplayName("TC-FMT05: Page break inserted via Ctrl+Enter creates a page-break node")
    void insertPageBreak_ctrlEnter_pageBreakNodeAppearsInEditor() {
        // given — designer is open with some text
        TemplateDesignerPage page = new TemplateDesignerPage(driver, wait).open(templateId);
        page.typeInEditor("Первая страница");

        // when — press Ctrl+Enter to insert a page break
        page.insertPageBreak();

        // then — the .page-break-node element is present inside the editor
        page.assertPageBreakExists();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String uuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
