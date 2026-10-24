package com.docforge.export;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves inline TipTap nodes that require runtime context:
 * <ul>
 *   <li>{@code templateParam} — substitutes {@code attrs.fieldId} with the value from the
 *       document answers map.</li>
 *   <li>{@code clauseRef} — substitutes {@code attrs.clauseId} with "п. X.Y" computed by
 *       walking the document tree and counting {@code numberedClause} nodes in order.</li>
 * </ul>
 *
 * <p>Typical usage: call {@link #buildClauseNumberingMap} once per document body, then pass
 * the resulting map to every {@link #resolveClauseRef} call.
 */
@Component
public class NodeResolver {

    private static final String EMPTY_PLACEHOLDER = "—";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves a {@code templateParam} node to a plain-text TipTap text node.
     *
     * @param node    the {@code templateParam} node; must have {@code attrs.fieldId}
     * @param answers the document answers map (may be null)
     * @return a TipTap {@code text} node with the resolved value, or "—" if absent
     */
    public Map<String, Object> resolveTemplateParam(
            Map<String, Object> node, Map<String, Object> answers) {
        String fieldId = getAttr(node, "fieldId");
        if (fieldId == null) return textNode(EMPTY_PLACEHOLDER);

        Object value = (answers != null) ? answers.get(fieldId) : null;
        String text = (value != null && !String.valueOf(value).isBlank())
                ? String.valueOf(value).trim()
                : EMPTY_PLACEHOLDER;
        return textNode(text);
    }

    /**
     * Resolves a {@code clauseRef} node to a plain-text TipTap text node ("п. X.Y").
     *
     * @param node            the {@code clauseRef} node; must have {@code attrs.clauseId}
     * @param clauseNumbering pre-built map of clauseId → hierarchical number string
     * @return a TipTap {@code text} node with "п. X.Y", or "п. ?" if the clause is not found
     */
    public Map<String, Object> resolveClauseRef(
            Map<String, Object> node, Map<String, String> clauseNumbering) {
        String clauseId = getAttr(node, "clauseId");
        if (clauseId == null) return textNode("п. ?");

        String number = clauseNumbering.get(clauseId);
        return textNode(number != null ? "п. " + number : "п. ?");
    }

    /**
     * Traverses the document body and assigns hierarchical numbers to all
     * {@code numberedClause} nodes (e.g., "1", "2", "2.1", "2.1.3").
     *
     * <p>Only direct {@code numberedClause} children of a clause (or of the doc root)
     * contribute to sequential counting. Other block nodes are not traversed.
     *
     * @param docBody the TipTap root node (type "doc")
     * @return map of clauseId → number string; insertion-ordered
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> buildClauseNumberingMap(Map<String, Object> docBody) {
        Map<String, String> result = new LinkedHashMap<>();
        if (docBody == null) return result;

        collectClauseNumbers(asNodeList(docBody.get("content")), result, new ArrayList<>());
        return result;
    }

    /** content узла как список узлов; мусор (не-массив) трактуем как пустоту. */
    @SuppressWarnings("unchecked")
    private List<Object> asNodeList(Object content) {
        return content instanceof List<?> list ? (List<Object>) list : null;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void collectClauseNumbers(
            List<Object> nodes, Map<String, String> result, List<Integer> prefix) {
        if (nodes == null) return;
        int seq = 0;
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> rawNode)) continue;
            Map<String, Object> node = (Map<String, Object>) rawNode;
            if (!"numberedClause".equals(node.get("type"))) continue;

            seq++;
            List<Integer> current = new ArrayList<>(prefix);
            current.add(seq);

            String clauseId = getAttr(node, "clauseId");
            if (clauseId != null) result.put(clauseId, joinNumbers(current));

            // Recurse into this clause's content for nested sub-clauses
            collectClauseNumbers(asNodeList(node.get("content")), result, current);
        }
    }

    private String joinNumbers(List<Integer> nums) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nums.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(nums.get(i));
        }
        return sb.toString();
    }

    private String getAttr(Map<String, Object> node, String key) {
        Object attrs = node.get("attrs");
        if (!(attrs instanceof Map<?, ?> m)) return null;
        Object val = m.get(key);
        return val != null ? val.toString() : null;
    }

    private Map<String, Object> textNode(String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }
}
