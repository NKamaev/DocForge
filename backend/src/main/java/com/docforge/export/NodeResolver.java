package com.docforge.export;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves inline TipTap nodes that require runtime context:
 * <ul>
 *   <li>{@code templateParam} — substitutes {@code attrs.fieldId} with the value from the
 *       document answers map.</li>
 *   <li>{@code clauseRef} — substitutes {@code attrs.refId} (legacy: {@code attrs.clauseId}) with
 *       "п. X.Y." computed by {@link ClauseNumbering} (same algorithm as the editor's
 *       {@code AutoNumberingPlugin}).</li>
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
     * Resolves a {@code clauseRef} node to a plain-text TipTap text node ("п. X.Y.").
     *
     * <p>TipTap-схема пишет ссылку в {@code attrs.refId} (см.
     * {@code ClauseRefNode}); {@code attrs.clauseId} поддерживается как
     * легаси-имя. Map {@code clauseNumbering} строится по {@code attrs.id}
     * пункта (BUG-065).
     *
     * <p>Метка — с завершающей точкой (формат редактора: {@code "1."}, {@code "2.1."}),
     * поэтому текст ссылки — {@code "п. 1."}, как в {@code ClauseRefNode}
     * ({@code п. {label}}) — BUG-066.
     *
     * @param node            the {@code clauseRef} node
     * @param clauseNumbering pre-built map of clauseId → label (editor format, with trailing dot)
     * @return a TipTap {@code text} node with "п. X.Y.", or "п. ?" if the clause is not found
     */
    public Map<String, Object> resolveClauseRef(
            Map<String, Object> node, Map<String, String> clauseNumbering) {
        String clauseId = firstAttr(node, "refId", "clauseId");
        if (clauseId == null) return textNode("п. ?");

        String label = clauseNumbering.get(clauseId);
        return textNode(label != null ? "п. " + label : "п. ?");
    }

    /**
     * Traverses the document body and assigns printed labels to all
     * {@code numberedClause} nodes (editor format: "1.", "2.1.", "а." — BUG-066).
     *
     * <p>Делегирует {@link ClauseNumbering#build} — точному порту алгоритма
     * {@code buildLabels} из редактора ({@code AutoNumberingPlugin}): обходятся
     * все узлы в порядке следования (семантика {@code doc.descendants}), уровни
     * берутся из {@code attrs.level}, стили — из {@code attrs.numberingStyle},
     * {@code attrs.listStart} начинает новый список. Скрытые пункты исключаются,
     * потому что вызывающие стороны передают уже отфильтрованное дерево (BUG-019).
     *
     * @param docBody the TipTap root node (type "doc")
     * @return map of clause id ({@code attrs.id}, legacy {@code attrs.clauseId})
     *         → label (with trailing dot); insertion-ordered
     */
    public Map<String, String> buildClauseNumberingMap(Map<String, Object> docBody) {
        return ClauseNumbering.build(docBody).byId();
    }

    private String getAttr(Map<String, Object> node, String key) {
        Object attrs = node.get("attrs");
        if (!(attrs instanceof Map<?, ?> m)) return null;
        Object val = m.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Первый непустой атрибут из списка ключей.
     *
     * <p>Схема TipTap и легаси-данные используют разные имена:
     * пункт хранит id в {@code attrs.id}, ссылка — в {@code attrs.refId},
     * старые документы писали {@code attrs.clauseId} (BUG-065).
     */
    private String firstAttr(Map<String, Object> node, String... keys) {
        for (String key : keys) {
            String value = getAttr(node, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Map<String, Object> textNode(String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }
}
