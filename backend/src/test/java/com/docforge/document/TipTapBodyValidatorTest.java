package com.docforge.document;

import com.docforge.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TipTapBodyValidator} — the write-boundary guard introduced by BUG-063.
 *
 * <p>{@code PUT /api/documents/{id}/body} accepts an arbitrary JSON value
 * ({@code UpdateBodyRequest(@NotNull Object currentBody)}), so before the fix
 * {@code current_body} could end up holding an array, a string, or a node with a
 * non-object {@code attrs}. The exporters then hit {@code ClassCastException}
 * (raw {@code (Map)/(String)} casts) and the request turned into a 500.</p>
 *
 * <p>The validator runs in {@code DocumentServiceImpl.updateBody} <em>before</em>
 * anything is persisted and throws {@link ValidationException}, which
 * {@code GlobalExceptionHandler} maps to {@code 422}. The rules are deliberately
 * soft (the tree comes from TipTap/Yjs and may grow): only the shapes the walkers
 * and exporters actually depend on are checked — nodes are objects,
 * {@code type}/{@code text} are strings, {@code attrs} is an object,
 * {@code content}/{@code marks} are arrays, every {@code marks} entry is an object
 * whose {@code type} is optional (but must be a string when present) — plus a recursion
 * depth limit.</p>
 *
 * <p>Pure unit test: no Spring, no mocks — just the validator and its contract.</p>
 */
class TipTapBodyValidatorTest {

    private final TipTapBodyValidator validator = new TipTapBodyValidator();

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A well-formed TipTap body passes (nested content, attrs objects, marks arrays)")
    void validate_validBody_doesNotThrow() {
        Map<String, Object> body = doc(
                node("heading", Map.of("level", 1), List.of(textNode("Договор"))),
                node("paragraph", null, List.of(
                        textNode("Заказчик: "),
                        node("parameterField", Map.of("parameterCode", "client_name", "display", "ФИО"), null),
                        markedTextNode("Иванов"))));

        assertThatCode(() -> validator.validate(body)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null body passes: export answers 422 on its own, the write boundary accepts emptiness")
    void validate_nullBody_doesNotThrow() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    // ── root must be an object ────────────────────────────────────────────────

    @Test
    @DisplayName("Root body = JSON array → ValidationException (was ClassCastException on export)")
    void validate_rootArray_throws() {
        assertRejected(List.of(doc()), "ожидается JSON-объект");
    }

    @Test
    @DisplayName("Root body = JSON string → ValidationException")
    void validate_rootString_throws() {
        assertRejected("мусор", "ожидается JSON-объект");
    }

    @Test
    @DisplayName("Root body = JSON number → ValidationException")
    void validate_rootNumber_throws() {
        assertRejected(42, "ожидается JSON-объект");
    }

    // ── node shape ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A non-object node inside content → ValidationException with its path")
    void validate_nonObjectNode_throws() {
        Map<String, Object> body = doc(node("paragraph", null, List.of("просто строка")));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Узел тела документа должен быть объектом");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].content[0]");
    }

    @Test
    @DisplayName("Node with a non-string \"type\" → ValidationException")
    void validate_nonStringType_throws() {
        Map<String, Object> body = doc(node("paragraph", null, List.of(node(42, null, null))));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле type узла должно быть строкой");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].content[0].type");
    }

    @Test
    @DisplayName("Node with a non-string \"text\" → ValidationException")
    void validate_nonStringText_throws() {
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", 7);
        Map<String, Object> body = doc(node("paragraph", null, List.of(textNode)));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле text узла должно быть строкой");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].content[0].text");
    }

    @Test
    @DisplayName("Node with string \"attrs\" (the legacy-garbage shape) → ValidationException")
    void validate_stringAttrs_throws() {
        Map<String, Object> body = doc(node("paragraph", "junk", null));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле attrs узла должно быть объектом");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].attrs");
    }

    @Test
    @DisplayName("Node with array \"attrs\" → ValidationException")
    void validate_arrayAttrs_throws() {
        Map<String, Object> body = doc(node("paragraph", List.of("junk"), null));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле attrs узла должно быть объектом");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].attrs");
    }

    @Test
    @DisplayName("Node with string \"marks\" → ValidationException")
    void validate_stringMarks_throws() {
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("type", "text");
        textNode.put("text", "x");
        textNode.put("marks", "junk");
        Map<String, Object> body = doc(node("paragraph", null, List.of(textNode)));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле marks узла должно быть массивом");
        assertThat(fieldErrorOf(body)).contains("currentBody.content[0].content[0].marks");
    }

    // ── mark entries ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Marks entry that is a string (marks: [\"bold\"]) → ValidationException with the …marks[0] path")
    void validate_markEntryAsString_throws() {
        Map<String, Object> paragraph = node("paragraph", null, null);
        paragraph.put("marks", new ArrayList<>(List.of("bold")));
        Map<String, Object> body = doc(paragraph);

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Метка marks должна быть объектом");
        assertThat(fieldErrorOf(body)).isEqualTo("currentBody.content[0].marks[0]");
    }

    @Test
    @DisplayName("Mark with a non-string \"type\" (marks: [{\"type\": 42}]) → ValidationException with the …marks[0].type path")
    void validate_markNonStringType_throws() {
        Map<String, Object> paragraph = node("paragraph", null, null);
        paragraph.put("marks", new ArrayList<>(List.of(Map.of("type", 42))));
        Map<String, Object> body = doc(paragraph);

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле type метки должно быть строкой");
        assertThat(fieldErrorOf(body)).isEqualTo("currentBody.content[0].marks[0].type");
    }

    @Test
    @DisplayName("Marks: [{\"type\":\"bold\"}] — a well-formed mark object passes")
    void validate_markObjectWithValidType_doesNotThrow() {
        Map<String, Object> body = doc(node("paragraph", null, List.of(markedTextNode("Иванов"))));

        assertThatCode(() -> validator.validate(body)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A mark entry deeper in the tree is reported with its full path")
    void validate_nestedMarkEntryAsString_throws() {
        Map<String, Object> textNode = textNode("x");
        textNode.put("marks", new ArrayList<>(List.of(Map.of("type", "bold"), "junk")));
        Map<String, Object> body = doc(node("paragraph", null, List.of(textNode)));

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Метка marks должна быть объектом");
        assertThat(fieldErrorOf(body)).isEqualTo("currentBody.content[0].content[0].marks[1]");
    }

    @Test
    @DisplayName("Node with string \"content\" → ValidationException")
    void validate_stringContent_throws() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "doc");
        body.put("content", "junk");

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Поле content узла должно быть массивом");
        assertThat(fieldErrorOf(body)).contains("currentBody.content");
    }

    // ── recursion depth ───────────────────────────────────────────────────────

    @Test
    @DisplayName("A tree nested deeper than MAX_DEPTH → ValidationException")
    void validate_treeBeyondDepthLimit_throws() {
        Map<String, Object> body = nestedChain(502);

        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("слишком глубоко вложено");
    }

    @Test
    @DisplayName("A tree exactly at MAX_DEPTH passes")
    void validate_treeAtDepthLimit_doesNotThrow() {
        assertThatCode(() -> validator.validate(nestedChain(501))).doesNotThrowAnyException();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Asserts that {@code body} is rejected and its {@code currentBody} field error is returned. */
    private void assertRejected(Object body, String expectedErrorFragment) {
        assertThatThrownBy(() -> validator.validate(body))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Тело документа");
        assertThat(fieldErrorOf(body)).contains(expectedErrorFragment);
    }

    /** Runs the validator and returns the {@code currentBody} field error (or {@code null} when accepted). */
    private String fieldErrorOf(Object body) {
        try {
            validator.validate(body);
            return null;
        } catch (ValidationException e) {
            return e.getFieldErrors().get("currentBody");
        }
    }

    /** Chain of {@code length} nested {@code content:[...]} nodes; node N sits at depth N-1. */
    private static Map<String, Object> nestedChain(int length) {
        Map<String, Object> deepest = node("paragraph", null, null);
        Map<String, Object> current = deepest;
        for (int i = 1; i < length; i++) {
            current = node("doc", null, List.of(current));
        }
        return current;
    }

    private static Map<String, Object> doc(Object... blocks) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("content", new ArrayList<>(List.of(blocks)));
        return doc;
    }

    private static Map<String, Object> node(Object type, Object attrs, List<Object> content) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        if (attrs != null) node.put("attrs", attrs);
        if (content != null) node.put("content", new ArrayList<>(content));
        return node;
    }

    private static Map<String, Object> textNode(String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    private static Map<String, Object> markedTextNode(String text) {
        Map<String, Object> node = textNode(text);
        node.put("marks", new ArrayList<>(List.of(Map.of("type", "bold"))));
        return node;
    }
}
