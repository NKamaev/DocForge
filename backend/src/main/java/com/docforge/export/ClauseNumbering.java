package com.docforge.export;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Вычисляет печатные метки ({@code "1."}, {@code "2.1."}, {@code "а."}) для узлов
 * {@code numberedClause}.
 *
 * <p><b>Точная копия</b> алгоритма {@code buildLabels} из редактора
 * ({@code frontend/src/components/template-designer/extensions/NumberedClauseNode.tsx},
 * {@code createAutoNumberingPlugin}) — печатная метка в DOCX/PDF должна совпадать
 * с тем, что показывает {@code AutoNumberingPlugin} (BUG-065, BUG-066):
 * <ul>
 *   <li>обходятся <b>все</b> узлы документа в порядке следования (семантика
 *       {@code doc.descendants}) — вложенность в другие block-узлы не скрывает
 *       пункт от нумерации;</li>
 *   <li>{@code level = clamp(attrs.level, 1..5)} (по умолчанию 1),
 *       {@code attrs.numberingStyle} ∈ {@code decimal | alpha-ru | alpha-en}
 *       (по умолчанию {@code decimal});</li>
 *   <li>{@code attrs.listStart} (truthy) сбрасывает <b>все</b> счётчики;</li>
 *   <li>счётчики уровней выше {@code level} обнуляются, затем
 *       {@code counters[level-1]++};</li>
 *   <li>метка = счётчики уровней 1..level, отформатированные стилями,
 *       запоминенными для своих уровней, склеенные через {@code '.'} с
 *       завершающей точкой (например {@code "2.1."}).</li>
 * </ul>
 *
 * <p><b>Скрытые пункты</b> (ложное условие) не участвуют в нумерации, потому что
 * вызывающие стороны строят метки по <b>отфильтрованному</b> дереву
 * (скрытые узлы удалены {@code ConditionalNodeFilter} ещё до экспорта — BUG-019).
 */
public final class ClauseNumbering {

    /** Максимальный уровень вложенности (как в редакторе). */
    private static final int MAX_LEVEL = 5;

    private static final String RU_ALPHA = "абвгдежзийклмнопрстуфхцчшщэюя";
    private static final String EN_ALPHA = "abcdefghijklmnopqrstuvwxyz";

    private static final String STYLE_DECIMAL = "decimal";
    private static final String STYLE_ALPHA_RU = "alpha-ru";
    private static final String STYLE_ALPHA_EN = "alpha-en";

    private ClauseNumbering() {
    }

    /**
     * Результат нумерации.
     *
     * @param byId   id пункта ({@code attrs.id}, легаси {@code attrs.clauseId}) → метка;
     *               пункт без id занимает номер, но в карту не попадает
     * @param byNode пункт (identity-ключ! метки зависят от порядка, equals/hashCode
     *               у JSON-узлов не различают позиции) → метка
     */
    public record Result(Map<String, String> byId, Map<Map<String, Object>, String> byNode) {

        /** Метка конкретного узла пункта или {@code null}, если узел не пункт. */
        public String labelFor(Map<String, Object> clauseNode) {
            return clauseNode != null ? byNode.get(clauseNode) : null;
        }
    }

    /**
     * Строит метки для всех {@code numberedClause} в документе.
     *
     * @param docBody TipTap-корень (type "doc"); {@code null} → пустая нумерация
     * @return метки по id и по узлам (identity)
     */
    public static Result build(Map<String, Object> docBody) {
        Map<String, String> byId = new LinkedHashMap<>();
        Map<Map<String, Object>, String> byNode = new IdentityHashMap<>();
        if (docBody == null) {
            return new Result(Map.of(), Map.of());
        }

        int[] counters = new int[MAX_LEVEL];
        String[] levelStyles = new String[MAX_LEVEL];
        Arrays.fill(levelStyles, STYLE_DECIMAL);

        walk(docBody, counters, levelStyles, byId, byNode);

        return new Result(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byNode));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Pre-order обход (семантика {@code doc.descendants}): узел, затем потомки. */
    @SuppressWarnings("unchecked")
    private static void walk(
            Map<String, Object> node,
            int[] counters,
            String[] levelStyles,
            Map<String, String> byId,
            Map<Map<String, Object>, String> byNode) {

        if ("numberedClause".equals(str(node.get("type")))) {
            Map<String, Object> attrs = asMap(node.get("attrs"));

            int level = toInt(attrs != null ? attrs.get("level") : null, 1);
            if (level == 0) level = 1; // редактор: (attrs.level as number) || 1
            level = Math.max(1, Math.min(MAX_LEVEL, level));

            String style = normalizeStyle(attrs != null ? attrs.get("numberingStyle") : null);

            if (attrs != null && isTruthy(attrs.get("listStart"))) {
                Arrays.fill(counters, 0);
            }
            for (int i = level; i < MAX_LEVEL; i++) {
                counters[i] = 0;
            }
            counters[level - 1]++;
            levelStyles[level - 1] = style;

            String label = buildLabel(counters, levelStyles, level);
            byNode.put(node, label);

            String id = firstAttr(node, "id", "clauseId");
            if (id != null) byId.put(id, label);
        }

        List<Object> children = asNodeList(node.get("content"));
        if (children == null) return;
        for (Object child : children) {
            if (child instanceof Map<?, ?> rawChild) {
                walk((Map<String, Object>) rawChild, counters, levelStyles, byId, byNode);
            }
        }
    }

    /** Метка: счётчики уровней 1..level в стилях своих уровней + завершающая точка. */
    private static String buildLabel(int[] counters, String[] levelStyles, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) sb.append('.');
            sb.append(formatCounter(counters[i], levelStyles[i]));
        }
        return sb.append('.').toString();
    }

    private static String formatCounter(int n, String style) {
        if (STYLE_ALPHA_RU.equals(style)) return letter(RU_ALPHA, n);
        if (STYLE_ALPHA_EN.equals(style)) return letter(EN_ALPHA, n);
        return String.valueOf(n);
    }

    /** Буква по номеру (1-based); за пределами алфавита — сам номер (как в редакторе). */
    private static String letter(String alphabet, int n) {
        if (n >= 1 && n <= alphabet.length()) return String.valueOf(alphabet.charAt(n - 1));
        return String.valueOf(n);
    }

    private static String normalizeStyle(Object value) {
        String style = str(value);
        if (STYLE_ALPHA_RU.equals(style) || STYLE_ALPHA_EN.equals(style)) return style;
        return STYLE_DECIMAL;
    }

    /** JS-truthy (семантика {@code if (node.attrs.listStart)} в редакторе). */
    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }

    /** content узла как список узлов; мусор (не-массив) трактуем как пустоту (BUG-063). */
    @SuppressWarnings("unchecked")
    private static List<Object> asNodeList(Object content) {
        return content instanceof List<?> list ? (List<Object>) list : null;
    }

    /** attrs как Map; мусор (не-объект) трактуется как отсутствие attrs (BUG-063). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** Строковое значение атрибута; не-строки не роняют нумерацию (BUG-063). */
    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    /**
     * Первый непустой атрибут из списка ключей: схема TipTap пишет id пункта в
     * {@code attrs.id}, легаси-данные — в {@code attrs.clauseId} (BUG-065).
     */
    private static String firstAttr(Map<String, Object> node, String... keys) {
        Map<String, Object> attrs = asMap(node.get("attrs"));
        if (attrs == null) return null;
        for (String key : keys) {
            Object raw = attrs.get(key);
            // toString (а не только String): id в JSON может прийти числом
            String value = raw != null ? raw.toString() : null;
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
