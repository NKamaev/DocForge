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
}
