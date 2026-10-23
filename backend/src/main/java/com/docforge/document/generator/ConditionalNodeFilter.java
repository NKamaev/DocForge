package com.docforge.document.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Убирает из TipTap-дерева контент, скрытый условиями вариативности.
 *
 * <p>Применяется перед экспортом DOCX/PDF: {@code current_body} документа может
 * содержать неразвёрнутые узлы (в редакторе на {@code /fill} условные блоки
 * показываются как узлы), а экспорт обязан содержать только видимый контент.
 *
 * <p>Поддерживаемые узлы:
 * <ul>
 *   <li>{@code conditionalBlock} — при истинном условии разворачивается в
 *       дочерние узлы, при ложном удаляется;</li>
 *   <li>{@code conditionalInline} — то же для inline-содержимого;</li>
 *   <li>{@code numberedClause} — при ложном условии удаляется целиком.</li>
 * </ul>
 *
 * <p>Пустое/отсутствующее условие означает «показывать всегда».
 *
 * <p>Обход собственный (не {@link TipTapTreeWalker}), потому что после
 * разворачивания блока его дочерние узлы должны быть проверены <b>заново</b>:
 * вложенный условный блок обязан вычисляться независимо от внешнего.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionalNodeFilter {

    private final ConditionEvaluator conditionEvaluator;
    private final PythonConditionEvaluator pythonConditionEvaluator;

    /**
     * @param body    TipTap-дерево (обычно {@code doc})
     * @param answers ответы опросного листа ({@code parameterCode → value})
     * @return отфильтрованная копия; исходное дерево не мутируется, неизменённые
     *         поддеревья переиспользуются (structural sharing)
     */
    public Map<String, Object> filter(Map<String, Object> body, Map<String, Object> answers) {
        if (body == null) return null;
        Map<String, Object> safeAnswers = answers != null ? answers : Map.of();

        // Корень — обычно doc; если это условный узел, разворачиваем и его.
        List<Map<String, Object>> processed = processNode(body, safeAnswers);
        if (processed.size() == 1) {
            return processed.get(0);
        }
        Map<String, Object> result = new LinkedHashMap<>(body);
        result.put("content", processed);
        return result;
    }

    /**
     * Обрабатывает один узел и возвращает то, что должно остаться на его месте:
     * {@code []} — удалить, {@code [node]} — оставить, список детей — развернуть.
     */
    private List<Map<String, Object>> processNode(
            Map<String, Object> node,
            Map<String, Object> answers) {

        String type = String.valueOf(node.get("type"));
        boolean block = "conditionalBlock".equals(type);
        boolean inline = "conditionalInline".equals(type);
        boolean clause = "numberedClause".equals(type);

        if (!block && !inline && !clause) {
            return List.of(withFilteredContent(node, answers));
        }

        Map<String, Object> condition = asMap(asMap(node.get("attrs")).get("condition"));
        String expression = condition.get("expression") instanceof String s ? s : null;

        if (expression != null && !expression.isBlank()
                && !evaluate(condition.get("type"), expression, answers)) {
            log.debug("Скрыт узел '{}' по условию '{}'", type, expression);
            return List.of();
        }

        if (block || inline) {
            // Разворачиваем и заново фильтруем каждого ребёнка — иначе вложенный
            // условный блок проскочит наружу невычисленным.
            List<Map<String, Object>> unwrapped = new ArrayList<>();
            for (Map<String, Object> child : contentOf(node)) {
                unwrapped.addAll(processNode(child, answers));
            }
            return unwrapped;
        }

        return List.of(withFilteredContent(node, answers));
    }

    /** Копия узла, у которой {@code content} пропущен через фильтр. */
    private Map<String, Object> withFilteredContent(
            Map<String, Object> node,
            Map<String, Object> answers) {

        if (!(node.get("content") instanceof List<?> content) || content.isEmpty()) {
            return node;
        }

        List<Object> filtered = new ArrayList<>(content.size());
        for (Object item : content) {
            if (item instanceof Map<?, ?> child) {
                filtered.addAll(processNode(asMap(child), answers));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(node);
        result.put("content", filtered);
        return result;
    }

    private boolean evaluate(Object rawType, String expression, Map<String, Object> answers) {
        String type = rawType != null ? String.valueOf(rawType) : "";
        if ("python".equalsIgnoreCase(type)) {
            try {
                return pythonConditionEvaluator.evaluate(expression, answers);
            } catch (ScriptExecutionException e) {
                // ошибка/таймаут скрипта — fail-closed, как в DocumentGenerator
                log.warn("Ошибка Python-условия '{}' (считаем ложным): {}", expression, e.getMessage());
                return false;
            } catch (IllegalArgumentException e) {
                // GraalPython недоступен на текущем рантайме (buildContext)
                log.warn("Python-условие '{}' не может быть вычислено (считаем ложным): {}",
                        expression, e.getMessage());
                return false;
            }
        }
        return conditionEvaluator.evaluate(expression, answers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(Map<String, Object> node) {
        if (!(node.get("content") instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
