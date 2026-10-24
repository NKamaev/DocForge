package com.docforge.document;

import com.docforge.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Проверка структуры TipTap-тела документа на границе записи
 * ({@code PUT /api/documents/{id}/body}).
 *
 * <p>Тело принимается как произвольный JSON ({@code Object}), поэтому в
 * {@code current_body} могли попадать массивы, строки и узлы с
 * не-объектными {@code attrs} — при экспорте это превращалось в
 * {@code ClassCastException} и 500 (BUG-063).
 *
 * <p>Правила сознательно мягкие (дерево приходит из TipTap/Yjs и может
 * расти): проверяется только то, от чего зависят обходчики и экспортёры —
 * узлы это объекты, {@code content}/{@code marks} — массивы, {@code attrs} —
 * объект, {@code type}/{@code text} — строки.
 */
@Component
public class TipTapBodyValidator {

    /** Защита от чрезмерно глубокого дерева (рекурсивный обход). */
    private static final int MAX_DEPTH = 500;

    /**
     * @throws ValidationException если тело не является деревом TipTap-узлов
     *         (422 вместо 500 при экспорте)
     */
    public void validate(Object body) {
        if (body == null) {
            return; // пустое тело допустимо: экспорт отвечает 422 своим ходом
        }
        if (!(body instanceof Map<?, ?>)) {
            throw new ValidationException(
                    "Тело документа должно быть объектом TipTap-дерева", Map.of("currentBody", "ожидается JSON-объект"));
        }
        validateNode(body, "currentBody", 0);
    }

    private void validateNode(Object node, String path, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ValidationException(
                    "Тело документа слишком глубоко вложено", Map.of("currentBody", path + ": превышена глубина " + MAX_DEPTH));
        }
        if (!(node instanceof Map<?, ?> map)) {
            throw new ValidationException(
                    "Узел тела документа должен быть объектом", Map.of("currentBody", path + ": ожидается JSON-объект"));
        }

        Object type = map.get("type");
        if (type != null && !(type instanceof String)) {
            throw new ValidationException(
                    "Поле type узла должно быть строкой", Map.of("currentBody", path + ".type"));
        }
        Object text = map.get("text");
        if (text != null && !(text instanceof String)) {
            throw new ValidationException(
                    "Поле text узла должно быть строкой", Map.of("currentBody", path + ".text"));
        }
        Object attrs = map.get("attrs");
        if (attrs != null && !(attrs instanceof Map<?, ?>)) {
            throw new ValidationException(
                    "Поле attrs узла должно быть объектом", Map.of("currentBody", path + ".attrs"));
        }
        Object marks = map.get("marks");
        if (marks != null && !(marks instanceof List<?>)) {
            throw new ValidationException(
                    "Поле marks узла должно быть массивом", Map.of("currentBody", path + ".marks"));
        }
        if (marks instanceof List<?> markList) {
            for (int i = 0; i < markList.size(); i++) {
                Object mark = markList.get(i);
                if (!(mark instanceof Map<?, ?> markMap)) {
                    throw new ValidationException(
                            "Метка marks должна быть объектом", Map.of("currentBody", path + ".marks[" + i + "]"));
                }
                Object markType = markMap.get("type");
                if (markType != null && !(markType instanceof String)) {
                    throw new ValidationException(
                            "Поле type метки должно быть строкой",
                            Map.of("currentBody", path + ".marks[" + i + "].type"));
                }
            }
        }

        Object content = map.get("content");
        if (content == null) {
            return;
        }
        if (!(content instanceof List<?> children)) {
            throw new ValidationException(
                    "Поле content узла должно быть массивом", Map.of("currentBody", path + ".content"));
        }
        for (int i = 0; i < children.size(); i++) {
            validateNode(children.get(i), path + ".content[" + i + "]", depth + 1);
        }
    }
}
