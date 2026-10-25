package com.docforge.export;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link ClauseNumbering} — порта {@code buildLabels} из редактора
 * ({@code AutoNumberingPlugin}). Экспортёры DOCX/PDF получают метки по узлам
 * ({@code byNode}), поэтому критичны: формат метки (с завершающей точкой) и
 * identity-семантика карты {@code byNode} (BUG-066).
 */
class ClauseNumberingTest {

    @Test
    void build_byNodeIsIdentityKeyed_structurallyEqualClausesGetOwnLabels() {
        // Два узла-пункта с одинаковыми атрибутами и содержимым (equals-равны!).
        // Карта по узлам обязана различать экземпляры: equals/hashCode-ключ
        // схлопнул бы их и оба пункта напечатали бы одну метку.
        Map<String, Object> first = clause("same", 1, paragraph(text("A")));
        Map<String, Object> second = clause("same", 1, paragraph(text("A")));
        assertThat(first).isEqualTo(second); // sanity: узлы equals-равны

        ClauseNumbering.Result result = ClauseNumbering.build(doc(first, second));

        assertThat(result.labelFor(first)).isEqualTo("1.");
        assertThat(result.labelFor(second)).isEqualTo("2.");
    }

    @Test
    void build_labelFor_unknownOrNullNode_returnsNull() {
        ClauseNumbering.Result result = ClauseNumbering.build(doc(clause("c1", 1, paragraph(text("X")))));

        assertThat(result.labelFor(Map.of("type", "paragraph"))).isNull();
        assertThat(result.labelFor(null)).isNull();
    }

    @Test
    void build_byId_legacyClauseIdFallback() {
        // Легаси-формат: id пункта хранится в attrs.clauseId (BUG-065)
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("clauseId", "old-id");
        attrs.put("level", 1);
        node.put("attrs", attrs);
        node.put("content", new ArrayList<>(List.of(paragraph(text("Y")))));

        ClauseNumbering.Result result = ClauseNumbering.build(doc(node));

        assertThat(result.byId()).containsEntry("old-id", "1.");
    }

    @Test
    void build_mixedLevelsAndStyles_labelsMatchEditor() {
        // level-2 пункт с numberingStyle "alpha-ru": стиль применяется к сегменту
        // СВОЕГО уровня → "1.а." (parity с AutoNumberingPlugin)
        Map<String, Object> nested = clauseAtLevel("c1_1", 2, "alpha-ru", paragraph(text("B")));
        Map<String, Object> root1 = clause("c1", 1, paragraph(text("A")), nested);
        Map<String, Object> root2 = clauseWithListStart("c2", true, 1, paragraph(text("C")));

        ClauseNumbering.Result result = ClauseNumbering.build(doc(root1, root2));

        assertThat(result.byId())
                .containsEntry("c1", "1.")
                .containsEntry("c1_1", "1.а.")
                // listStart сбрасывает все счётчики → второй список снова с "1."
                .containsEntry("c2", "1.");
        assertThat(result.labelFor(nested)).isEqualTo("1.а.");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    @SafeVarargs
    private final Map<String, Object> doc(Map<String, Object>... children) {
        return node("doc", null, children);
    }

    @SafeVarargs
    private final Map<String, Object> clause(String id, int level, Map<String, Object>... children) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", id);
        attrs.put("level", level);
        return node("numberedClause", attrs, children);
    }

    @SafeVarargs
    private final Map<String, Object> clauseAtLevel(
            String id, int level, String style, Map<String, Object>... children) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", id);
        attrs.put("level", level);
        attrs.put("numberingStyle", style);
        return node("numberedClause", attrs, children);
    }

    @SafeVarargs
    private final Map<String, Object> clauseWithListStart(
            String id, boolean listStart, int level, Map<String, Object>... children) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", id);
        attrs.put("level", level);
        attrs.put("listStart", listStart);
        return node("numberedClause", attrs, children);
    }

    @SafeVarargs
    private final Map<String, Object> paragraph(Map<String, Object>... inlines) {
        return node("paragraph", null, inlines);
    }

    private Map<String, Object> text(String value) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", value);
        return node;
    }

    @SafeVarargs
    private final Map<String, Object> node(
            String type, Map<String, Object> attrs, Map<String, Object>... children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        if (attrs != null) node.put("attrs", new LinkedHashMap<>(attrs));
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }
}
