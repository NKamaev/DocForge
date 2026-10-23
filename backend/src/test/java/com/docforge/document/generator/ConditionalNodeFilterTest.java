package com.docforge.document.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConditionalNodeFilter} — regression guard for BUG-019
 * («Скрытые по условию блоки попадают в DOCX и PDF при экспорте»).
 *
 * <p>{@code current_body} документа может содержать неразвёрнутые узлы
 * вариативности: в редакторе на {@code /fill} условные блоки и inline-условия
 * живут как узлы. Перед экспортом фильтр обязан привести дерево к «видимому»
 * виду — иначе скрытый текст утекает в файл.</p>
 *
 * <p>Контракт, который пиннится здесь:</p>
 * <ul>
 *   <li>{@code conditionalBlock} / {@code conditionalInline}: условие ложно — узел
 *       удаляется целиком, истинно — узел разворачивается в своё содержимое
 *       (дети сохраняются);</li>
 *   <li>{@code numberedClause}: условие ложно — пункт удаляется, истинно — пункт
 *       остаётся собой (нумерация считается уже после фильтрации);</li>
 *   <li>вложенность любой глубины: развёрнутый блок не «проносит» скрытого ребёнка —
 *       каждый поднявшийся узел вычисляется заново (true → true → false);</li>
 *   <li>пустое/отсутствующее условие = «показывать всегда» (блок разворачивается,
 *       пункт остаётся);</li>
 *   <li>{@code type: "python"} делегируется в {@link PythonConditionEvaluator},
 *       любая ошибка исполнения считается ложью (fail-closed);</li>
 *   <li>корень тоже может быть условным узлом: ложное условие → узел с пустым
 *       {@code content}, истинное → дети блока становятся {@code content} корня;</li>
 *   <li>обычные узлы проходят нетронутыми, а входное дерево не мутируется.</li>
 * </ul>
 *
 * <p>Реальный {@link ConditionEvaluator} + мок {@link PythonConditionEvaluator}, потому что
 * GraalPython на тестовой JVM не требуется: цель теста — маршрутизация условий, а не сам
 * sandbox (он покрыт {@link PythonConditionEvaluatorTest}).</p>
 *
 * <p>Отдельная причина держать {@link TipTapTreeWalker} вне этого класса: фильтр обходит
 * дерево <b>сам</b> ({@code processNode} / {@code withFilteredContent}), поэтому узел,
 * поднявшийся на место развёрнутого условного блока, проверяется заново. Именно этого
 * не хватало до фикса BUG-019 — «висячий» вложенный блок утекал в DOCX/PDF
 * (см. {@code Nesting#filter_givenOuterTrueBlockWithInnerFalseBlock_dropsOnlyInner}).</p>
 */
class ConditionalNodeFilterTest {

    /** has_penalty == false → условные узлы с этим условием скрыты. */
    private static final Map<String, Object> HIDDEN_ANSWERS = Map.of("has_penalty", false);

    /** has_penalty == true → те же узлы видимы. */
    private static final Map<String, Object> VISIBLE_ANSWERS = Map.of("has_penalty", true);

    private static final String CONDITION = "has_penalty == true";

    private PythonConditionEvaluator pythonEvaluator;
    private ConditionalNodeFilter filter;

    @BeforeEach
    void setUp() {
        pythonEvaluator = mock(PythonConditionEvaluator.class);
        // Обход собственный (у фильтра больше нет зависимости от TipTapTreeWalker):
        // реальный ConditionEvaluator + мок Python-оценщика.
        filter = new ConditionalNodeFilter(new ConditionEvaluator(), pythonEvaluator);
    }

    // ── conditionalBlock ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("conditionalBlock")
    class ConditionalBlock {

        @Test
        @DisplayName("filter with a false condition removes the whole block (BUG-019)")
        void filter_givenFalseConditionalBlock_removesBlock() {
            // given — скрытый блок и видимый сосед
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("HIDDEN_BLOCK_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then — от блока не остаётся ни узла, ни текста
            assertThat(contentOf(result)).containsExactly(paragraph(text("VISIBLE_TEXT")));
            assertThat(textsIn(result)).doesNotContain("HIDDEN_BLOCK_TEXT").contains("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("filter with a true condition unwraps the block and keeps its children")
        void filter_givenTrueConditionalBlock_unwrapsBlockKeepingChildren() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_BLOCK_TEXT"))),
                    paragraph(text("AFTER")));

            // when
            Map<String, Object> result = filter.filter(body, VISIBLE_ANSWERS);

            // then — содержимое поднялось на уровень документа, самого узла нет
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("VISIBLE_BLOCK_TEXT")),
                    paragraph(text("AFTER")));
            assertThat(textsIn(result)).containsExactly("VISIBLE_BLOCK_TEXT", "AFTER");
        }

        @Test
        @DisplayName("filter with no condition at all unwraps the block (visible by default)")
        void filter_givenBlockWithoutCondition_unwrapsBlock() {
            // given — attrs есть, но без "condition"
            Map<String, Object> block = base("conditionalBlock");
            block.put("attrs", new LinkedHashMap<>(Map.of("label", "Блок без условия")));
            block.put("content", new ArrayList<>(List.of(paragraph(text("ALWAYS_VISIBLE")))));
            Map<String, Object> body = doc(block);

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(paragraph(text("ALWAYS_VISIBLE")));
        }

        @Test
        @DisplayName("filter with a null condition unwraps the block (visible by default)")
        void filter_givenBlockWithNullCondition_unwrapsBlock() {
            // given — attrs.condition присутствует, но null
            Map<String, Object> block = base("conditionalBlock");
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("condition", null);
            block.put("attrs", attrs);
            block.put("content", new ArrayList<>(List.of(paragraph(text("ALWAYS_VISIBLE")))));
            Map<String, Object> body = doc(block);

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then
            assertThat(textsIn(result)).containsExactly("ALWAYS_VISIBLE");
        }

        @Test
        @DisplayName("filter with a blank expression unwraps the block (treated as always visible)")
        void filter_givenBlockWithBlankExpression_unwrapsBlock() {
            // given — пустое условие «забыли заполнить» не должно скрывать текст
            Map<String, Object> body = doc(
                    conditionalBlock("   ", paragraph(text("ALWAYS_VISIBLE"))));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(paragraph(text("ALWAYS_VISIBLE")));
        }

        @Test
        @DisplayName("filter without answers treats a false-looking condition as hidden, no NPE")
        void filter_givenNullAnswers_hidesBlockWithoutThrowing() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("HIDDEN_BLOCK_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = filter.filter(body, null);

            // then
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }
    }

    // ── conditionalInline ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("conditionalInline")
    class ConditionalInline {

        @Test
        @DisplayName("filter with a false condition removes the inline fragment, siblings survive (BUG-019)")
        void filter_givenFalseConditionalInline_removesInlineFragment() {
            // given — скрытый фрагмент между двумя видимыми
            Map<String, Object> body = doc(paragraph(
                    text("до "),
                    conditionalInline(CONDITION, text("HIDDEN_INLINE_TEXT")),
                    text(" после")));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then — остаётся одна параграфная строка без скрытого фрагмента
            assertThat(contentOf(firstChild(result))).containsExactly(text("до "), text(" после"));
            assertThat(textsIn(result)).containsExactly("до ", " после");
        }

        @Test
        @DisplayName("filter with a true condition unwraps the inline fragment in place")
        void filter_givenTrueConditionalInline_unwrapsInlineChildren() {
            // given
            Map<String, Object> body = doc(paragraph(
                    text("до "),
                    conditionalInline(CONDITION, text("VISIBLE_INLINE_TEXT")),
                    text(" после")));

            // when
            Map<String, Object> result = filter.filter(body, VISIBLE_ANSWERS);

            // then — порядок inline-детей сохранён, узла conditionalInline нет
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("до "),
                    text("VISIBLE_INLINE_TEXT"),
                    text(" после"));
        }
    }

    // ── numberedClause ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("numberedClause")
    class NumberedClause {

        @Test
        @DisplayName("filter with a false clause condition removes the clause entirely")
        void filter_givenFalseNumberedClause_removesClause() {
            // given — скрытый пункт и обычный пункт без условия
            Map<String, Object> body = doc(
                    numberedClause(CONDITION, "clause-1", paragraph(text("HIDDEN_CLAUSE_TEXT"))),
                    numberedClause(null, "clause-2", paragraph(text("KEPT_CLAUSE_TEXT"))));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then — скрытый пункт не должен ни рендериться, ни попадать в нумерацию
            assertThat(contentOf(result)).containsExactly(
                    numberedClause(null, "clause-2", paragraph(text("KEPT_CLAUSE_TEXT"))));
            assertThat(textsIn(result)).containsExactly("KEPT_CLAUSE_TEXT");
        }

        @Test
        @DisplayName("filter with a true clause condition keeps the clause as a node (not unwrapped)")
        void filter_givenTrueNumberedClause_keepsClauseAsNode() {
            // given
            Map<String, Object> expectedClause =
                    numberedClause(CONDITION, "clause-1", paragraph(text("VISIBLE_CLAUSE_TEXT")));
            Map<String, Object> body = doc(expectedClause);

            // when
            Map<String, Object> result = filter.filter(body, VISIBLE_ANSWERS);

            // then — пункт остался пунктом: нумерация считается уже после фильтра
            assertThat(contentOf(result)).containsExactly(expectedClause);
            assertThat(contentOf(result).get(0).get("type")).isEqualTo("numberedClause");
        }

        @Test
        @DisplayName("filter with no clause condition keeps the clause as a node")
        void filter_givenClauseWithoutCondition_keepsClause() {
            // given
            Map<String, Object> body = doc(
                    numberedClause(null, "clause-1", paragraph(text("VISIBLE_CLAUSE_TEXT"))));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(result)).hasSize(1);
            assertThat(contentOf(result).get(0).get("type")).isEqualTo("numberedClause");
            assertThat(textsIn(result)).containsExactly("VISIBLE_CLAUSE_TEXT");
        }
    }

    // ── nesting ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("nested conditionals")
    class Nesting {

        @Test
        @DisplayName("an outer false block hides nested (even visible) content")
        void filter_givenOuterFalseBlockWithInnerTrueBlock_hidesEverything() {
            // given — внешний блок скрыт, внутренний сам по себе был бы виден
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION,
                            paragraph(text("OUTER_HIDDEN_TEXT")),
                            conditionalBlock("penalty_percent > 10",
                                    paragraph(text("INNER_HIDDEN_TEXT")))),
                    paragraph(text("VISIBLE_TEXT")));

            Map<String, Object> answers = Map.of("has_penalty", false, "penalty_percent", 15);

            // when
            Map<String, Object> result = filter.filter(body, answers);

            // then
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("an outer true block unwraps while its nested false block is dropped")
        void filter_givenOuterTrueBlockWithInnerFalseBlock_dropsOnlyInner() {
            // given — внешний блок виден, вложенный скрыт: после разворачивания внешнего
            // вложенный узел встаёт на его место и обязан быть вычислен заново
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION,
                            paragraph(text("OUTER_VISIBLE_TEXT")),
                            conditionalBlock("penalty_percent > 10",
                                    paragraph(text("INNER_HIDDEN_TEXT")))));

            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);

            // when
            Map<String, Object> result = filter.filter(body, answers);

            // then — внешний блок развернулся, внутренний исчез
            assertThat(contentOf(result)).containsExactly(paragraph(text("OUTER_VISIBLE_TEXT")));
            assertThat(textsIn(result)).containsExactly("OUTER_VISIBLE_TEXT");
        }

        @Test
        @DisplayName("a nested conditionalInline hidden inside a visible conditionalInline is dropped")
        void filter_givenOuterTrueInlineWithInnerFalseInline_dropsOnlyInner() {
            // given — тот же сценарий для inline-уровня: внешний фрагмент виден,
            // вложенный скрыт (penalty_percent = 5 ≯ 10)
            Map<String, Object> body = doc(paragraph(
                    text("начало "),
                    conditionalInline(CONDITION,
                            text("INLINE_VISIBLE"),
                            conditionalInline("penalty_percent > 10", text("INLINE_HIDDEN"))),
                    text(" конец")));

            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);

            // when
            Map<String, Object> result = filter.filter(body, answers);

            // then — порядок соседей сохранён, скрытого фрагмента (и узлов) больше нет
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("начало "),
                    text("INLINE_VISIBLE"),
                    text(" конец"));
            assertThat(textsIn(result)).containsExactly("начало ", "INLINE_VISIBLE", " конец");
        }

        @Test
        @DisplayName("three-level nesting (true → true → false) unwraps the visible levels only")
        void filter_givenThreeLevelNesting_dropsOnlyInnermostHidden() {
            // given — 1-й и 2-й уровни видимы, 3-й скрыт; пинним именно рекурсию,
            // а не один шаг разворачивания
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION,
                            paragraph(text("LEVEL_1_TEXT")),
                            conditionalBlock("penalty_percent > 0",
                                    paragraph(text("LEVEL_2_TEXT")),
                                    conditionalBlock("penalty_percent > 10",
                                            paragraph(text("LEVEL_3_HIDDEN_TEXT"))))),
                    paragraph(text("AFTER")));

            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);

            // when
            Map<String, Object> result = filter.filter(body, answers);

            // then — видимые уровни развёрнуты по порядку, скрытый исчез целиком
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("LEVEL_1_TEXT")),
                    paragraph(text("LEVEL_2_TEXT")),
                    paragraph(text("AFTER")));
            assertThat(textsIn(result)).containsExactly(
                    "LEVEL_1_TEXT", "LEVEL_2_TEXT", "AFTER");
        }
    }

    // ── python conditions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("python conditions")
    class PythonConditions {

        @Test
        @DisplayName("filter delegates a python condition to PythonConditionEvaluator")
        void filter_givenPythonCondition_delegatesToPythonEvaluator() {
            // given — GraalPython не нужен: оценщик замокан
            String expression = "result = has_penalty and penalty_percent > 10";
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 15);
            when(pythonEvaluator.evaluate(expression, answers)).thenReturn(true);
            Map<String, Object> body = doc(
                    pythonBlock(expression, paragraph(text("PYTHON_VISIBLE_TEXT"))));

            // when
            Map<String, Object> result = filter.filter(body, answers);

            // then — делегирование с теми же expression/answers, блок развёрнут
            verify(pythonEvaluator).evaluate(expression, answers);
            assertThat(contentOf(result)).containsExactly(paragraph(text("PYTHON_VISIBLE_TEXT")));
        }

        @Test
        @DisplayName("filter treats an uppercase PYTHON type the same way")
        void filter_givenUppercasePythonType_delegatesToPythonEvaluator() {
            // given — старые шаблоны хранят тип как "PYTHON"
            String expression = "result = True";
            Map<String, Object> conditions = new LinkedHashMap<>();
            conditions.put("type", "PYTHON");
            conditions.put("expression", expression);
            when(pythonEvaluator.evaluate(anyString(), anyMap())).thenReturn(false);

            Map<String, Object> body = doc(
                    conditionalBlockWithCondition(conditions, paragraph(text("HIDDEN_PY_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = filter.filter(body, Map.of());

            // then
            verify(pythonEvaluator).evaluate(expression, Map.of());
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("filter treats a python exception as a false condition (fail-closed)")
        void filter_givenPythonEvaluatorThrows_treatsConditionAsFalse() {
            // given — таймаут/синтаксическая ошибка/NameError
            when(pythonEvaluator.evaluate(anyString(), anyMap()))
                    .thenThrow(new ScriptExecutionException("Python script timed out after 2 seconds"));

            Map<String, Object> body = doc(
                    pythonBlock("result = undefined_name",
                            paragraph(text("HIDDEN_PY_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when — исключение не должно валить экспорт
            Map<String, Object> result = filter.filter(body, Map.of());

            // then
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("filter does not touch the python evaluator for simple conditions")
        void filter_givenSimpleCondition_doesNotCallPythonEvaluator() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_TEXT"))));

            // when
            filter.filter(body, VISIBLE_ANSWERS);

            // then
            verifyNoInteractions(pythonEvaluator);
        }
    }

    // ── pass-through / immutability ───────────────────────────────────────────

    @Nested
    @DisplayName("non-conditional content")
    class PassThrough {

        @Test
        @DisplayName("filter leaves a tree without conditional nodes untouched")
        void filter_givenNoConditionalNodes_passesTreeThroughUnchanged() {
            // given — заголовок, абзац с параметром, пункт без условия
            Map<String, Object> heading = base("heading");
            heading.put("attrs", Map.of("level", 1));
            heading.put("content", new ArrayList<>(List.of(text("ДОГОВОР"))));

            Map<String, Object> body = doc(
                    heading,
                    paragraph(text("Заказчик: "), parameterField("client_name")),
                    numberedClause(null, "clause-1", paragraph(text("1. Предмет договора"))));

            // when
            Map<String, Object> result = filter.filter(body, VISIBLE_ANSWERS);

            // then
            assertThat(result).isEqualTo(body);
            assertThat(textsIn(result)).containsExactly(
                    "ДОГОВОР", "Заказчик: ", "1. Предмет договора");
        }

        @Test
        @DisplayName("filter does not mutate the input tree")
        void filter_givenConditionalBody_doesNotMutateInputTree() {
            // given — два независимо построенных, но структурно равных дерева
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_BLOCK_TEXT"))),
                    paragraph(text("AFTER")));
            Map<String, Object> pristine = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_BLOCK_TEXT"))),
                    paragraph(text("AFTER")));
            @SuppressWarnings("unchecked")
            List<Object> originalContent = (List<Object>) body.get("content");

            // when
            filter.filter(body, VISIBLE_ANSWERS);

            // then — исходное дерево (и его вложенные списки) осталось как было
            assertThat(body).isEqualTo(pristine);
            assertThat(body.get("content")).isSameAs(originalContent);
        }

        @Test
        @DisplayName("filter returns null for a null body")
        void filter_givenNullBody_returnsNull() {
            assertThat(filter.filter(null, VISIBLE_ANSWERS)).isNull();
        }
    }

    // ── root node handling ────────────────────────────────────────────────────

    /**
     * Корень дерева обычно {@code doc}, но фильтр не должен полагаться на это:
     * {@code filter()} прогоняет через {@code processNode} сам корень, а не только
     * его детей. Иначе условный корень с ложным условием протёк бы в экспорт
     * целиком.
     */
    @Nested
    @DisplayName("root node itself is conditional")
    class RootNode {

        @Test
        @DisplayName("a false-conditional root leaves a node with empty content")
        void filter_givenFalseConditionalBlockAsRoot_returnsNodeWithEmptyContent() {
            // given — условный блок в роли корня (не doc)
            Map<String, Object> body =
                    conditionalBlock(CONDITION, paragraph(text("HIDDEN_ROOT_TEXT")));

            // when
            Map<String, Object> result = filter.filter(body, HIDDEN_ANSWERS);

            // then — корень вычислен: содержимого нет, скрытый текст не просочился
            assertThat(result).isNotNull();
            assertThat(contentOf(result)).isEmpty();
            assertThat(textsIn(result)).isEmpty();
        }

        @Test
        @DisplayName("a true-conditional root exposes its children as root content")
        void filter_givenTrueConditionalBlockAsRoot_returnsChildrenAsRootContent() {
            // given — два ребёнка, чтобы результат не схлопнулся в единственный узел
            Map<String, Object> body = conditionalBlock(CONDITION,
                    paragraph(text("ROOT_CHILD_ONE")),
                    paragraph(text("ROOT_CHILD_TWO")));

            // when
            Map<String, Object> result = filter.filter(body, VISIBLE_ANSWERS);

            // then — условие корня вычислено, дети подняты в content корня по порядку
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("ROOT_CHILD_ONE")),
                    paragraph(text("ROOT_CHILD_TWO")));
            assertThat(textsIn(result)).containsExactly("ROOT_CHILD_ONE", "ROOT_CHILD_TWO");
            // Python-оценщик не задействован — условие простое
            verifyNoInteractions(pythonEvaluator);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> base(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        return node;
    }

    private static Map<String, Object> doc(Map<String, Object>... blocks) {
        Map<String, Object> node = base("doc");
        node.put("content", new ArrayList<>(List.of(blocks)));
        return node;
    }

    private static Map<String, Object> paragraph(Object... inlines) {
        Map<String, Object> node = base("paragraph");
        node.put("content", new ArrayList<>(List.of(inlines)));
        return node;
    }

    private static Map<String, Object> text(String value) {
        Map<String, Object> node = base("text");
        node.put("text", value);
        return node;
    }

    private static Map<String, Object> parameterField(String code) {
        Map<String, Object> node = base("parameterField");
        node.put("attrs", Map.of("parameterCode", code, "display", code));
        return node;
    }

    private static Map<String, Object> simpleCondition(String expression) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "simple");
        condition.put("expression", expression);
        return condition;
    }

    private static Map<String, Object> conditionalBlock(String expression, Object... children) {
        return conditionalBlockWithCondition(simpleCondition(expression), children);
    }

    private static Map<String, Object> pythonBlock(String expression, Object... children) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "python");
        condition.put("expression", expression);
        return conditionalBlockWithCondition(condition, children);
    }

    private static Map<String, Object> conditionalBlockWithCondition(
            Map<String, Object> condition, Object... children) {
        Map<String, Object> node = base("conditionalBlock");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("condition", condition);
        attrs.put("label", "Блок неустойки");
        node.put("attrs", attrs);
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }

    private static Map<String, Object> conditionalInline(String expression, Object... inlines) {
        Map<String, Object> node = base("conditionalInline");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("condition", simpleCondition(expression));
        attrs.put("label", "Условный текст");
        node.put("attrs", attrs);
        node.put("content", new ArrayList<>(List.of(inlines)));
        return node;
    }

    /** {@code condition == null} → attrs без ключа "condition" (реальный кейс «условие не задано»). */
    private static Map<String, Object> numberedClause(
            String expression, String id, Object... children) {
        Map<String, Object> node = base("numberedClause");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("level", 1);
        attrs.put("numberingStyle", "decimal");
        attrs.put("id", id);
        if (expression != null) attrs.put("condition", simpleCondition(expression));
        node.put("attrs", attrs);
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> contentOf(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("content");
    }

    private static Map<String, Object> firstChild(Map<String, Object> node) {
        return contentOf(node).get(0);
    }

    /** Все текстовые значения дерева в порядке обхода — удобно для «скрытого текста нет». */
    private static List<String> textsIn(Map<String, Object> node) {
        List<String> texts = new ArrayList<>();
        collectTexts(node, texts);
        return texts;
    }

    @SuppressWarnings("unchecked")
    private static void collectTexts(Map<String, Object> node, List<String> out) {
        if (node.get("text") instanceof String value) {
            out.add(value);
        }
        if (node.get("content") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    collectTexts((Map<String, Object>) childMap, out);
                }
            }
        }
    }
}
