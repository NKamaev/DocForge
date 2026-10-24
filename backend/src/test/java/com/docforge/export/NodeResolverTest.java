package com.docforge.export;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NodeResolverTest {

    private final NodeResolver resolver = new NodeResolver();

    // ── resolveTemplateParam ──────────────────────────────────────────────────

    @Test
    void resolveTemplateParam_returnsAnswerValue() {
        Map<String, Object> node = Map.of(
                "type", "templateParam",
                "attrs", Map.of("fieldId", "clientName")
        );
        Map<String, Object> answers = Map.of("clientName", "Иванов Иван Иванович");

        Map<String, Object> result = resolver.resolveTemplateParam(node, answers);

        assertThat(result.get("type")).isEqualTo("text");
        assertThat(result.get("text")).isEqualTo("Иванов Иван Иванович");
    }

    @Test
    void resolveTemplateParam_fieldAbsent_returnsEmDash() {
        Map<String, Object> node = Map.of(
                "type", "templateParam",
                "attrs", Map.of("fieldId", "missing")
        );
        Map<String, Object> answers = Map.of("clientName", "Иванов");

        Map<String, Object> result = resolver.resolveTemplateParam(node, answers);

        assertThat(result.get("type")).isEqualTo("text");
        assertThat(result.get("text")).isEqualTo("—");
    }

    @Test
    void resolveTemplateParam_nullAnswers_returnsEmDash() {
        Map<String, Object> node = Map.of(
                "type", "templateParam",
                "attrs", Map.of("fieldId", "anything")
        );

        Map<String, Object> result = resolver.resolveTemplateParam(node, null);

        assertThat(result.get("text")).isEqualTo("—");
    }

    @Test
    void resolveTemplateParam_blankValue_returnsEmDash() {
        Map<String, Object> node = Map.of(
                "type", "templateParam",
                "attrs", Map.of("fieldId", "field")
        );
        Map<String, Object> answers = Map.of("field", "   ");

        Map<String, Object> result = resolver.resolveTemplateParam(node, answers);

        assertThat(result.get("text")).isEqualTo("—");
    }

    @Test
    void resolveTemplateParam_noFieldIdAttr_returnsEmDash() {
        Map<String, Object> node = Map.of(
                "type", "templateParam",
                "attrs", Map.of()
        );

        Map<String, Object> result = resolver.resolveTemplateParam(node, Map.of("x", "y"));

        assertThat(result.get("text")).isEqualTo("—");
    }

    // ── buildClauseNumberingMap ───────────────────────────────────────────────

    @Test
    void buildClauseNumberingMap_topLevelClauses_sequential() {
        Map<String, Object> body = doc(List.of(
                clause("c1"),
                clause("c2"),
                clause("c3")
        ));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsEntry("c1", "1")
                        .containsEntry("c2", "2")
                        .containsEntry("c3", "3");
    }

    @Test
    void buildClauseNumberingMap_nestedClauses_hierarchical() {
        Map<String, Object> c2 = clauseWithChildren("c2", List.of(
                clause("c2_1"),
                clause("c2_2")
        ));
        Map<String, Object> body = doc(List.of(clause("c1"), c2, clause("c3")));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsEntry("c1",   "1")
                        .containsEntry("c2",   "2")
                        .containsEntry("c2_1", "2.1")
                        .containsEntry("c2_2", "2.2")
                        .containsEntry("c3",   "3");
    }

    @Test
    void buildClauseNumberingMap_deepNesting() {
        Map<String, Object> c1_1_1 = clause("c1_1_1");
        Map<String, Object> c1_1 = clauseWithChildren("c1_1", List.of(c1_1_1));
        Map<String, Object> c1 = clauseWithChildren("c1", List.of(c1_1));
        Map<String, Object> body = doc(List.of(c1));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsEntry("c1",     "1")
                        .containsEntry("c1_1",   "1.1")
                        .containsEntry("c1_1_1", "1.1.1");
    }

    @Test
    void buildClauseNumberingMap_emptyBody_returnsEmptyMap() {
        assertThat(resolver.buildClauseNumberingMap(null)).isEmpty();
        assertThat(resolver.buildClauseNumberingMap(doc(List.of()))).isEmpty();
    }

    // ── resolveClauseRef ──────────────────────────────────────────────────────

    @Test
    void resolveClauseRef_knownId_returnsFormattedLabel() {
        Map<String, Object> body = doc(List.of(clause("c1"), clauseWithChildren("c2", List.of(clause("c2_1")))));
        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        Map<String, Object> refNode = Map.of(
                "type", "clauseRef",
                "attrs", Map.of("clauseId", "c2_1")
        );

        Map<String, Object> result = resolver.resolveClauseRef(refNode, nums);

        assertThat(result.get("type")).isEqualTo("text");
        assertThat(result.get("text")).isEqualTo("п. 2.1");
    }

    @Test
    void resolveClauseRef_unknownId_returnsQuestionMark() {
        Map<String, Object> refNode = Map.of(
                "type", "clauseRef",
                "attrs", Map.of("clauseId", "nonexistent")
        );

        Map<String, Object> result = resolver.resolveClauseRef(refNode, Map.of());

        assertThat(result.get("text")).isEqualTo("п. ?");
    }

    @Test
    void resolveClauseRef_noClauseIdAttr_returnsQuestionMark() {
        Map<String, Object> refNode = Map.of(
                "type", "clauseRef",
                "attrs", Map.of()
        );

        Map<String, Object> result = resolver.resolveClauseRef(refNode, Map.of("x", "1"));

        assertThat(result.get("text")).isEqualTo("п. ?");
    }

    // ── BUG-065: реальная TipTap-схема — attrs.id у пункта, attrs.refId у ссылки ──
    // NumberedClauseNode пишет id пункта в attrs.id, ClauseRefNode — цель в attrs.refId.
    // Раньше NodeResolver читал attrs.clauseId для обоих, поэтому карта нумерации
    // на реальных документах была всегда пустой и каждая ссылка экспортировалась «п. ?».

    @Test
    void buildClauseNumberingMap_realSchemaAttrs_keyedById() {
        Map<String, Object> body = doc(List.of(
                clauseWithId("c1"),
                clauseWithId("c2"),
                clauseWithId("c3")));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsEntry("c1", "1")
                        .containsEntry("c2", "2")
                        .containsEntry("c3", "3");
    }

    @Test
    void buildClauseNumberingMap_realSchemaAttrs_nestedClausesHierarchical() {
        Map<String, Object> c2 = clauseWithIdChildren("c2", List.of(
                clauseWithId("c2_1"),
                clauseWithId("c2_2")));
        Map<String, Object> body = doc(List.of(clauseWithId("c1"), c2, clauseWithId("c3")));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsEntry("c1",   "1")
                        .containsEntry("c2",   "2")
                        .containsEntry("c2_1", "2.1")
                        .containsEntry("c2_2", "2.2")
                        .containsEntry("c3",   "3");
    }

    @Test
    void buildClauseNumberingMap_idAttrWinsOverLegacyClauseId_whenBothPresent() {
        Map<String, Object> body = doc(List.of(
                clauseNode(Map.of("id", "new-id", "clauseId", "old-id"), List.of())));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsOnlyKeys("new-id").containsEntry("new-id", "1");
    }

    @Test
    void buildClauseNumberingMap_blankId_fallsBackToLegacyClauseId() {
        // зеркало resolveClauseRef_blankRefId_fallsBackToLegacyClauseId для карты:
        // пустой attrs.id не должен затирать легаси-идентификатор пункта
        Map<String, Object> body = doc(List.of(
                clauseNode(Map.of("id", "   ", "clauseId", "c1"), List.of())));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsOnlyKeys("c1").containsEntry("c1", "1");

        Map<String, Object> legacyRef = Map.of("type", "clauseRef", "attrs", Map.of("clauseId", "c1"));
        assertThat(resolver.resolveClauseRef(legacyRef, nums).get("text")).isEqualTo("п. 1");
    }

    @Test
    void buildClauseNumberingMap_clauseInsideNonClauseBlock_isNotTraversed() {
        // Документировано в javadoc: счёт ведут только прямые numberedClause-дети
        // корня doc и пунктов; прочие block-узлы не обходятся.
        Map<String, Object> body = doc(List.of(
                blockNode(List.of(clauseWithId("buried"))),
                clauseWithId("c1")));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsOnlyKeys("c1").containsEntry("c1", "1");
    }

    @Test
    void buildClauseNumberingMap_clauseWithoutId_consumesNumberButIsNotMapped() {
        // видимый пункт без id занимает номер (в документе он нумерован),
        // но целью ссылки быть не может
        Map<String, Object> body = doc(List.of(
                clauseNode(Map.of(), List.of()),
                clauseWithId("c2")));

        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(nums).containsOnlyKeys("c2").containsEntry("c2", "2");
    }

    @Test
    void resolveClauseRef_realSchemaRefId_knownId_returnsFormattedLabel() {
        Map<String, Object> body = doc(List.of(
                clauseWithId("c1"),
                clauseWithIdChildren("c2", List.of(clauseWithId("c2_1")))));
        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        Map<String, Object> result = resolver.resolveClauseRef(clauseRef("c2_1"), nums);

        assertThat(result.get("type")).isEqualTo("text");
        assertThat(result.get("text")).isEqualTo("п. 2.1");
    }

    @Test
    void clauseRef_realSchema_idClauseReferencedByRefId_endToEnd() {
        Map<String, Object> body = doc(List.of(
                clauseWithId("c1"),
                clauseWithIdChildren("c2", List.of(clauseWithId("c2_1"))),
                clauseWithId("c3")));
        Map<String, String> nums = resolver.buildClauseNumberingMap(body);

        assertThat(resolver.resolveClauseRef(clauseRef("c1"), nums).get("text")).isEqualTo("п. 1");
        assertThat(resolver.resolveClauseRef(clauseRef("c2_1"), nums).get("text")).isEqualTo("п. 2.1");
        assertThat(resolver.resolveClauseRef(clauseRef("c3"), nums).get("text")).isEqualTo("п. 3");
    }

    @Test
    void clauseRef_mixedShapes_legacyRefToRealSchemaClause_resolves() {
        Map<String, String> nums = resolver.buildClauseNumberingMap(doc(List.of(clauseWithId("c1"))));

        Map<String, Object> legacyRef = Map.of("type", "clauseRef", "attrs", Map.of("clauseId", "c1"));

        assertThat(resolver.resolveClauseRef(legacyRef, nums).get("text")).isEqualTo("п. 1");
    }

    @Test
    void clauseRef_mixedShapes_realSchemaRefToLegacyClause_resolves() {
        Map<String, String> nums = resolver.buildClauseNumberingMap(doc(List.of(clause("c1"))));

        assertThat(resolver.resolveClauseRef(clauseRef("c1"), nums).get("text")).isEqualTo("п. 1");
    }

    @Test
    void resolveClauseRef_unknownRefId_returnsQuestionMark() {
        Map<String, Object> result = resolver.resolveClauseRef(clauseRef("nonexistent"), Map.of());

        assertThat(result.get("text")).isEqualTo("п. ?");
    }

    @Test
    void resolveClauseRef_noAttrsAtAll_returnsQuestionMark() {
        Map<String, Object> refNode = Map.of("type", "clauseRef");

        Map<String, Object> result = resolver.resolveClauseRef(refNode, Map.of("c1", "1"));

        assertThat(result.get("text")).isEqualTo("п. ?");
    }

    @Test
    void resolveClauseRef_blankRefId_fallsBackToLegacyClauseId() {
        Map<String, Object> refNode = Map.of(
                "type", "clauseRef",
                "attrs", Map.of("refId", "   ", "clauseId", "c1"));

        Map<String, Object> result = resolver.resolveClauseRef(refNode, Map.of("c1", "1"));

        assertThat(result.get("text")).isEqualTo("п. 1");
    }

    @Test
    void resolveClauseRef_refIdWinsOverLegacyClauseId_whenBothPresent() {
        Map<String, Object> refNode = Map.of(
                "type", "clauseRef",
                "attrs", Map.of("refId", "c2", "clauseId", "c1"));

        Map<String, Object> result = resolver.resolveClauseRef(refNode, Map.of("c1", "1", "c2", "2"));

        assertThat(result.get("text")).isEqualTo("п. 2");
    }

    // ── Node builders ─────────────────────────────────────────────────────────

    private Map<String, Object> doc(List<Map<String, Object>> content) {
        return Map.of("type", "doc", "content", content);
    }

    private Map<String, Object> clause(String id) {
        return clauseWithChildren(id, List.of());
    }

    private Map<String, Object> clauseWithChildren(String id, List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        node.put("attrs", Map.of("clauseId", id));
        node.put("content", new ArrayList<>(children));
        return node;
    }

    /** Пункт в реальной TipTap-схеме — идентификатор в {@code attrs.id} (BUG-065). */
    private Map<String, Object> clauseWithId(String id) {
        return clauseWithIdChildren(id, List.of());
    }

    private Map<String, Object> clauseWithIdChildren(String id, List<Map<String, Object>> children) {
        return clauseNode(Map.of("id", id), children);
    }

    private Map<String, Object> clauseNode(
            Map<String, Object> attrs, List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "numberedClause");
        node.put("attrs", new LinkedHashMap<>(attrs));
        node.put("content", new ArrayList<>(children));
        return node;
    }

    /** Ссылка на пункт в реальной TipTap-схеме — цель в {@code attrs.refId} (BUG-065). */
    private Map<String, Object> clauseRef(String refId) {
        return Map.of("type", "clauseRef", "attrs", Map.of("refId", refId));
    }

    /** Прочий block-узел (не пункт) — его содержимое нумерация не обходит. */
    private Map<String, Object> blockNode(List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "blockquote");
        node.put("content", new ArrayList<>(children));
        return node;
    }
}
