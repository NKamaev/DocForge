package com.docforge.document.generator;

import com.docforge.common.format.FioFormatterService;
import com.docforge.common.format.NumberToWordsService;
import com.docforge.template.model.Condition;
import com.docforge.template.model.ConditionType;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.ParameterType;
import com.docforge.template.model.Phrasing;
import com.docforge.template.model.PhrasingVariant;
import com.docforge.template.model.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentGenerator#generate(Template, Map)} — regression guard
 * for BUG-064 («Единая семантика условий: DocumentGenerator не обрабатывает conditionalInline»).
 *
 * <p>До фикса генерация имела собственную логику условий: она вырезала {@code conditionalBlock}
 * и {@code numberedClause}, но <b>не</b> {@code conditionalInline} — скрытый inline-текст
 * оставался в {@code generated_body}. Кроме того, узел <b>без</b> {@code attrs.condition}
 * оставался узлом, тогда как экспортный фильтр ({@link ConditionalNodeFilter}) его
 * разворачивал — две разные семантики одного и того же дерева.</p>
 *
 * <p>После фикса генерация целиком делегирует фильтрацию условий общему
 * {@link ConditionalNodeFilter} — тому же, что используется на пути экспорта. Фильтр
 * запускается после раскрытия {@code phrasingsRef} и перед подстановкой параметров,
 * поэтому условные узлы внутри развёрнутых вариантов формулировок тоже вычисляются.</p>
 *
 * <p>Закреплённый контракт:</p>
 * <ul>
 *   <li>{@code conditionalInline}: ложное условие — фрагмент удаляется, истинное —
 *       разворачивается на месте (порядок соседей сохраняется);</li>
 *   <li>{@code conditionalBlock} / {@code numberedClause}: ложное условие — узел удаляется
 *       (регрессия прежнего поведения);</li>
 *   <li>отсутствующее/пустое условие = «показывать всегда»: {@code conditionalBlock} и
 *       {@code conditionalInline} разворачиваются, {@code numberedClause} остаётся узлом;</li>
 *   <li>вложенность: внешний истинный + внутренний ложный (block и inline) — удаляется
 *       только внутренний;</li>
 *   <li>условный узел внутри раскрытого варианта {@code phrasingsRef} вычисляется тоже
 *       (фильтр выполняется после раскрытия);</li>
 *   <li>подстановка {@code parameterField} продолжает работать после фильтрации;</li>
 *   <li>скрытый контент не утекает в {@code generated_body}: {@code parameterField} внутри
 *       ложного блока не подставляется и не остаётся узлом, {@code numberedClause} внутри
 *       скрытого блока уходит вместе со всем поддеревом;</li>
 *   <li>{@code phrasingsRef} без подходящего варианта удаляется (узел не остаётся в выдаче);</li>
 *   <li>отказ Python-движка (любая {@link RuntimeException}, не только
 *       {@link ScriptExecutionException}) — fail-closed: условие ложно, генерация не падает.</li>
 * </ul>
 *
 * <p>Реальные {@link ConditionEvaluator}, {@link TipTapTreeWalker}, {@link ParameterSubstitutor}
 * и {@link ConditionalNodeFilter} — чтобы прогонялся настоящий pipeline генерации.
 * {@link PythonConditionEvaluator} замокан: GraalPython на тестовой JVM не нужен,
 * Python-условия проверяют маршрутизацию и fail-closed (sandbox покрыт
 * {@link PythonConditionEvaluatorTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentGeneratorTest {

    /** has_penalty == false → условные узлы с этим условием скрыты. */
    private static final Map<String, Object> HIDDEN_ANSWERS = Map.of("has_penalty", false);

    /** has_penalty == true → те же узлы видимы. */
    private static final Map<String, Object> VISIBLE_ANSWERS = Map.of("has_penalty", true);

    private static final String CONDITION = "has_penalty == true";

    @Mock private PythonConditionEvaluator pythonEvaluator;
    // Кллабораторы ParameterSubstitutor'а — замоканы, чтобы построить реальный субститутор.
    @Mock private NumberToWordsService numberToWords;
    @Mock private FioFormatterService fioFormatter;

    private DocumentGenerator generator;

    @BeforeEach
    void setUp() {
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
        generator = new DocumentGenerator(
                conditionEvaluator,
                pythonEvaluator,
                new TipTapTreeWalker(),
                new ParameterSubstitutor(numberToWords, fioFormatter),
                new ConditionalNodeFilter(conditionEvaluator, pythonEvaluator));
    }

    // ── conditionalInline (ядро BUG-064) ─────────────────────────────────────

    @Nested
    @DisplayName("conditionalInline (BUG-064)")
    class ConditionalInline {

        @Test
        @DisplayName("generate with a false condition removes the inline fragment from the body")
        void generate_givenFalseConditionalInline_removesInlineFragment() {
            // given — скрытый inline-фрагмент между двумя видимыми текстами
            Map<String, Object> body = doc(paragraph(
                    text("pre "),
                    conditionalInline(CONDITION, text("HIDDEN_INLINE")),
                    text(" post")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then — до фикса HIDDEN_INLINE оставался в generated_body
            assertThat(contentOf(firstChild(result))).containsExactly(text("pre "), text(" post"));
            assertThat(textsIn(result)).containsExactly("pre ", " post");
        }

        @Test
        @DisplayName("generate with a true condition unwraps the inline fragment in place, order preserved")
        void generate_givenTrueConditionalInline_unwrapsInlineChildrenInPlace() {
            // given
            Map<String, Object> body = doc(paragraph(
                    text("pre "),
                    conditionalInline(CONDITION, text("VISIBLE_INLINE")),
                    text(" post")));

            // when
            Map<String, Object> result = generate(body, VISIBLE_ANSWERS);

            // then — узла conditionalInline нет, содержимое встало на его место
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("pre "),
                    text("VISIBLE_INLINE"),
                    text(" post"));
            assertThat(textsIn(result)).containsExactly("pre ", "VISIBLE_INLINE", " post");
        }
    }

    // ── conditionalBlock / numberedClause (регрессия прежнего поведения) ─────

    @Nested
    @DisplayName("conditionalBlock / numberedClause with a false condition")
    class BlockAndClauseRegression {

        @Test
        @DisplayName("generate with a false block condition removes the whole block")
        void generate_givenFalseConditionalBlock_removesBlock() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("HIDDEN_BLOCK_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(paragraph(text("VISIBLE_TEXT")));
            assertThat(textsIn(result)).doesNotContain("HIDDEN_BLOCK_TEXT").contains("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("generate with a false clause condition removes the clause, the other stays a node")
        void generate_givenFalseNumberedClause_removesClause() {
            // given — скрытый пункт и видимый пункт
            Map<String, Object> keptClause =
                    numberedClause(null, "clause-2", paragraph(text("KEPT_CLAUSE_TEXT")));
            Map<String, Object> body = doc(
                    numberedClause(CONDITION, "clause-1", paragraph(text("HIDDEN_CLAUSE_TEXT"))),
                    keptClause);

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then — скрытый пункт не рендерится, видимый остаётся именно узлом numberedClause
            assertThat(contentOf(result)).containsExactly(keptClause);
            assertThat(contentOf(result).get(0).get("type")).isEqualTo("numberedClause");
            assertThat(textsIn(result)).containsExactly("KEPT_CLAUSE_TEXT");
        }

        @Test
        @DisplayName("generate removes a numberedClause nested inside a hidden block together with the block")
        void generate_givenNumberedClauseInsideHiddenBlock_removesWholeSubtree() {
            // given — пункт вложен в скрытый блок: уходить должно всё поддерево
            // (класс утечек BUG-019 — «скрытый пункт остался в выдаче»)
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION,
                            paragraph(text("HIDDEN_BLOCK_TEXT")),
                            numberedClause(null, "clause-1", paragraph(text("HIDDEN_CLAUSE_TEXT")))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then — ни блока, ни пункта, ни их текста в generated_body нет
            assertThat(contentOf(result)).containsExactly(paragraph(text("VISIBLE_TEXT")));
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
            assertThat(typesIn(result)).doesNotContain("conditionalBlock", "numberedClause");
        }

        @Test
        @DisplayName("generate with a true condition unwraps the block and keeps its children")
        void generate_givenTrueConditionalBlock_unwrapsBlockKeepingChildren() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_BLOCK_TEXT"))),
                    paragraph(text("AFTER")));

            // when
            Map<String, Object> result = generate(body, VISIBLE_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("VISIBLE_BLOCK_TEXT")),
                    paragraph(text("AFTER")));
        }

        @Test
        @DisplayName("generate does not touch the python evaluator for simple conditions")
        void generate_givenSimpleCondition_doesNotCallPythonEvaluator() {
            // given
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(text("VISIBLE_TEXT"))));

            // when
            generate(body, VISIBLE_ANSWERS);

            // then — простые условия решает ConditionEvaluator
            verifyNoInteractions(pythonEvaluator);
        }
    }

    // ── отсутствующее / пустое условие = «показывать всегда» ─────────────────

    @Nested
    @DisplayName("missing / blank condition (always visible)")
    class MissingCondition {

        @Test
        @DisplayName("generate unwraps a conditionalBlock without a condition (content visible)")
        void generate_givenBlockWithoutCondition_unwrapsBlock() {
            // given — attrs есть, но ключа "condition" нет
            Map<String, Object> body = doc(
                    blockWithoutCondition(paragraph(text("BLOCK_ALWAYS_VISIBLE"))),
                    paragraph(text("AFTER")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then — до фикса узел остался бы узлом (в экспорте он разворачивался)
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("BLOCK_ALWAYS_VISIBLE")),
                    paragraph(text("AFTER")));
            assertThat(textsIn(result)).containsExactly("BLOCK_ALWAYS_VISIBLE", "AFTER");
        }

        @Test
        @DisplayName("generate unwraps a conditionalInline without a condition (content visible)")
        void generate_givenInlineWithoutCondition_unwrapsInline() {
            // given
            Map<String, Object> body = doc(paragraph(
                    text("pre "),
                    inlineWithoutCondition(text("INLINE_ALWAYS_VISIBLE")),
                    text(" post")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("pre "),
                    text("INLINE_ALWAYS_VISIBLE"),
                    text(" post"));
        }

        @Test
        @DisplayName("generate unwraps block/inline nodes with a blank condition expression")
        void generate_givenBlankConditionExpression_treatsNodeAsVisible() {
            // given — «условие забыли заполнить» не должно скрывать текст
            Map<String, Object> body = doc(
                    conditionalBlock("   ", paragraph(text("BLANK_COND_BLOCK_VISIBLE"))),
                    paragraph(text("pre "),
                            conditionalInline("  ", text("BLANK_COND_INLINE_VISIBLE")),
                            text(" post")));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("BLANK_COND_BLOCK_VISIBLE")),
                    paragraph(text("pre "), text("BLANK_COND_INLINE_VISIBLE"), text(" post")));
            assertThat(textsIn(result)).containsExactly(
                    "BLANK_COND_BLOCK_VISIBLE", "pre ", "BLANK_COND_INLINE_VISIBLE", " post");
        }

        @Test
        @DisplayName("generate keeps a numberedClause without a condition as a node")
        void generate_givenClauseWithoutCondition_keepsClauseAsNode() {
            // given
            Map<String, Object> body = doc(
                    numberedClause(null, "clause-1", paragraph(text("VISIBLE_CLAUSE_TEXT"))));

            // when
            Map<String, Object> result = generate(body, HIDDEN_ANSWERS);

            // then — пункт остаётся пунктом: нумерация считается после фильтрации
            assertThat(contentOf(result)).hasSize(1);
            assertThat(contentOf(result).get(0).get("type")).isEqualTo("numberedClause");
            assertThat(textsIn(result)).containsExactly("VISIBLE_CLAUSE_TEXT");
        }
    }

    // ── вложенные условия ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("nested conditionals")
    class Nesting {

        @Test
        @DisplayName("an outer true block unwraps while its nested false block is dropped")
        void generate_givenOuterTrueBlockWithInnerFalseBlock_dropsOnlyInner() {
            // given — внешний блок виден (has_penalty), вложенный скрыт (penalty_percent ≯ 10)
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION,
                            paragraph(text("OUTER_VISIBLE_TEXT")),
                            conditionalBlock("penalty_percent > 10",
                                    paragraph(text("INNER_HIDDEN_TEXT")))));
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);

            // when
            Map<String, Object> result = generate(body, answers);

            // then — развёрнутый внешний блок не «проносит» скрытый внутренний
            assertThat(contentOf(result)).containsExactly(paragraph(text("OUTER_VISIBLE_TEXT")));
            assertThat(textsIn(result)).containsExactly("OUTER_VISIBLE_TEXT");
        }

        @Test
        @DisplayName("an outer true inline unwraps while its nested false inline is dropped")
        void generate_givenOuterTrueInlineWithInnerFalseInline_dropsOnlyInner() {
            // given — тот же сценарий на inline-уровне
            Map<String, Object> body = doc(paragraph(
                    text("start "),
                    conditionalInline(CONDITION,
                            text("INLINE_VISIBLE"),
                            conditionalInline("penalty_percent > 10", text("INLINE_HIDDEN"))),
                    text(" end")));
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);

            // when
            Map<String, Object> result = generate(body, answers);

            // then — порядок соседей сохранён, скрытый фрагмент исчез целиком
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("start "),
                    text("INLINE_VISIBLE"),
                    text(" end"));
            assertThat(textsIn(result)).containsExactly("start ", "INLINE_VISIBLE", " end");
        }
    }

    // ── phrasingsRef: условия внутри раскрытых вариантов ─────────────────────

    @Nested
    @DisplayName("conditional nodes inside expanded phrasingsRef variants")
    class PhrasingsRefExpansion {

        @Test
        @DisplayName("a false conditionalInline inside an expanded variant is dropped")
        void generate_givenFalseConditionalInlineInsideExpandedVariant_dropsIt() {
            // given — phrasingsRef внутри абзаца раскрывается в inline-вариант,
            // внутри которого есть условный фрагмент
            Map<String, Object> body = doc(paragraph(
                    text("before "),
                    phrasingsRef("ph1"),
                    text(" after")));
            Template template = templateWithPhrasings(body, List.of(
                    text("VARIANT_TEXT"),
                    conditionalInline(CONDITION, text("VARIANT_HIDDEN_INLINE"))));

            // when
            Map<String, Object> result = generate(template, HIDDEN_ANSWERS);

            // then — фильтр условий выполняется после раскрытия формулировки
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("before "),
                    text("VARIANT_TEXT"),
                    text(" after"));
            assertThat(textsIn(result)).containsExactly("before ", "VARIANT_TEXT", " after");
        }

        @Test
        @DisplayName("a true conditionalInline inside an expanded variant is unwrapped in place")
        void generate_givenTrueConditionalInlineInsideExpandedVariant_unwrapsIt() {
            // given
            Map<String, Object> body = doc(paragraph(
                    text("before "),
                    phrasingsRef("ph1"),
                    text(" after")));
            Template template = templateWithPhrasings(body, List.of(
                    text("VARIANT_TEXT"),
                    conditionalInline(CONDITION, text("VARIANT_VISIBLE_INLINE"))));

            // when
            Map<String, Object> result = generate(template, VISIBLE_ANSWERS);

            // then
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("before "),
                    text("VARIANT_TEXT"),
                    text("VARIANT_VISIBLE_INLINE"),
                    text(" after"));
        }

        @Test
        @DisplayName("a false conditionalBlock inside an expanded variant is dropped")
        void generate_givenFalseConditionalBlockInsideExpandedVariant_dropsIt() {
            // given — phrasingsRef на уровне блоков: вариант содержит условный
            // и безусловный блоки
            Map<String, Object> body = doc(
                    paragraph(text("before")),
                    phrasingsRef("ph1"),
                    paragraph(text("after")));
            Template template = templateWithPhrasings(body, List.of(
                    conditionalBlock(CONDITION, paragraph(text("VARIANT_HIDDEN_BLOCK"))),
                    paragraph(text("VARIANT_VISIBLE_BLOCK"))));

            // when
            Map<String, Object> result = generate(template, HIDDEN_ANSWERS);

            // then — скрытый блок из варианта не утёк в generated_body
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("before")),
                    paragraph(text("VARIANT_VISIBLE_BLOCK")),
                    paragraph(text("after")));
            assertThat(textsIn(result)).doesNotContain("VARIANT_HIDDEN_BLOCK");
        }

        @Test
        @DisplayName("a true conditionalBlock inside an expanded variant is unwrapped")
        void generate_givenTrueConditionalBlockInsideExpandedVariant_unwrapsIt() {
            // given
            Map<String, Object> body = doc(
                    paragraph(text("before")),
                    phrasingsRef("ph1"),
                    paragraph(text("after")));
            Template template = templateWithPhrasings(body, List.of(
                    conditionalBlock(CONDITION, paragraph(text("VARIANT_VISIBLE_BLOCK")))));

            // when
            Map<String, Object> result = generate(template, VISIBLE_ANSWERS);

            // then
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("before")),
                    paragraph(text("VARIANT_VISIBLE_BLOCK")),
                    paragraph(text("after")));
        }
    }

    // ── phrasingsRef: ссылка без подходящего варианта ────────────────────────

    @Nested
    @DisplayName("phrasingsRef without a matching variant")
    class PhrasingsRefDrop {

        @Test
        @DisplayName("generate drops a phrasingsRef whose phrasing does not exist")
        void generate_givenUnknownPhrasingsRef_dropsNode() {
            // given — ссылка на несуществующую формулировку
            Map<String, Object> body = doc(
                    paragraph(text("before")),
                    phrasingsRef("missing"),
                    paragraph(text("after")));
            Template template = templateWithPhrasings(body, List.of(text("VARIANT_TEXT")));

            // when
            Map<String, Object> result = generate(template, HIDDEN_ANSWERS);

            // then — узла phrasingsRef в выдаче не остаётся
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("before")),
                    paragraph(text("after")));
            assertThat(typesIn(result)).doesNotContain("phrasingsRef");
        }

        @Test
        @DisplayName("generate drops a phrasingsRef when no variant condition matches")
        void generate_givenPhrasingsRefWithoutMatchingVariant_dropsNode() {
            // given — единственный вариант формулировки имеет невыполненное условие
            Map<String, Object> body = doc(
                    paragraph(text("before")),
                    phrasingsRef("ph1"),
                    paragraph(text("after")));
            Template template = templateWithVariantList(body, List.of(
                    PhrasingVariant.builder()
                            .id("v1")
                            .condition(Condition.builder()
                                    .type(ConditionType.SIMPLE)
                                    .expression(CONDITION)
                                    .build())
                            .content(List.of(text("VARIANT_TEXT")))
                            .build()));

            // when
            Map<String, Object> result = generate(template, HIDDEN_ANSWERS);

            // then — подходящего варианта нет → узел удаляется, а не остаётся как есть
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("before")),
                    paragraph(text("after")));
            assertThat(textsIn(result)).doesNotContain("VARIANT_TEXT");
            assertThat(typesIn(result)).doesNotContain("phrasingsRef");
        }
    }

    // ── регрессия: подстановка параметров после фильтрации ───────────────────

    @Nested
    @DisplayName("parameterField substitution after filtering")
    class ParameterSubstitution {

        @Test
        @DisplayName("generate substitutes a parameterField next to a removed inline fragment")
        void generate_givenParameterFieldNextToFalseInline_substitutesValue() {
            // given — параметр подставляется, скрытый суффикс вырезается
            Map<String, Object> body = doc(paragraph(
                    text("Заказчик: "),
                    parameterField("client_name"),
                    conditionalInline(CONDITION, text(" (скрытый комментарий)"))));
            Template template = Template.builder()
                    .body(body)
                    .parameters(List.of(Parameter.builder()
                            .code("client_name")
                            .label("ФИО клиента")
                            .type(ParameterType.TEXT)
                            .build()))
                    .build();
            Map<String, Object> answers =
                    Map.of("client_name", "Иванов Иван Иванович", "has_penalty", false);

            // when
            Map<String, Object> result = generate(template, answers);

            // then — подстановка работает после фильтрации, скрытого текста нет
            assertThat(contentOf(firstChild(result))).containsExactly(
                    text("Заказчик: "),
                    text("Иванов Иван Иванович"));
            assertThat(textsIn(result)).doesNotContain(" (скрытый комментарий)");
        }

        @Test
        @DisplayName("generate substitutes a parameterField inside a kept (true) conditional block")
        void generate_givenParameterFieldInsideTrueBlock_substitutesValue() {
            // given — параметр живёт внутри условного блока, который виден
            Map<String, Object> body = doc(
                    conditionalBlock(CONDITION, paragraph(
                            text("Неустойка: "),
                            parameterField("penalty_percent"),
                            text("%"))));
            Template template = Template.builder()
                    .body(body)
                    .parameters(List.of(Parameter.builder()
                            .code("penalty_percent")
                            .label("Процент неустойки")
                            .type(ParameterType.NUMBER)
                            .build()))
                    .build();
            Map<String, Object> answers =
                    Map.of("has_penalty", true, "penalty_percent", 15);

            // when
            Map<String, Object> result = generate(template, answers);

            // then — блок развёрнут, значение отформатировано и подставлено
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("Неустойка: "), text("15"), text("%")));
        }

        @Test
        @DisplayName("generate drops a parameterField inside hidden content without leaking its value")
        void generate_givenParameterFieldInsideHiddenBlock_leaksNeitherValueNorNode() {
            // given — единственный параметр живёт внутри скрытого блока: его значение
            // не должно утекать в generated_body ни в каком виде
            Map<String, Object> body = doc(
                    paragraph(text("Обычный текст")),
                    conditionalBlock(CONDITION, paragraph(
                            text("Скрытая подпись: "),
                            parameterField("client_name"))));
            Template template = Template.builder()
                    .body(body)
                    .parameters(List.of(Parameter.builder()
                            .code("client_name")
                            .label("ФИО клиента")
                            .type(ParameterType.TEXT)
                            .build()))
                    .build();
            Map<String, Object> answers =
                    Map.of("client_name", "Иванов Иван Иванович", "has_penalty", false);

            // when
            Map<String, Object> result = generate(template, answers);

            // then — ни подставленного значения, ни узла parameterField, ни текста блока
            assertThat(contentOf(result)).containsExactly(paragraph(text("Обычный текст")));
            assertThat(textsIn(result))
                    .doesNotContain("Иванов Иван Иванович")
                    .doesNotContain("Скрытая подпись: ")
                    .doesNotContain("—"); // placeholder скрытого параметра тоже не утекает
            assertThat(typesIn(result)).doesNotContain("parameterField", "conditionalBlock");
        }
    }

    // ── python-условия (мок PythonConditionEvaluator) ────────────────────────

    @Nested
    @DisplayName("python conditions")
    class PythonConditions {

        @Test
        @DisplayName("generate delegates a python condition to PythonConditionEvaluator and unwraps on true")
        void generate_givenPythonConditionTrue_unwrapsBlock() {
            // given
            String expression = "result = has_penalty and penalty_percent > 10";
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 15);
            when(pythonEvaluator.evaluate(expression, answers)).thenReturn(true);
            Map<String, Object> body = doc(
                    pythonBlock(expression, paragraph(text("PYTHON_VISIBLE_TEXT"))),
                    paragraph(text("AFTER")));

            // when
            Map<String, Object> result = generate(body, answers);

            // then — делегирование с теми же expression/answers, блок развёрнут
            verify(pythonEvaluator).evaluate(expression, answers);
            assertThat(contentOf(result)).containsExactly(
                    paragraph(text("PYTHON_VISIBLE_TEXT")),
                    paragraph(text("AFTER")));
        }

        @Test
        @DisplayName("generate drops a block whose python condition is false")
        void generate_givenPythonConditionFalse_dropsBlock() {
            // given
            String expression = "result = has_penalty and penalty_percent > 10";
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);
            when(pythonEvaluator.evaluate(expression, answers)).thenReturn(false);
            Map<String, Object> body = doc(
                    pythonBlock(expression, paragraph(text("HIDDEN_PY_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = generate(body, answers);

            // then
            verify(pythonEvaluator).evaluate(expression, answers);
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("generate drops an inline fragment whose python condition is false")
        void generate_givenPythonConditionFalse_dropsInlineFragment() {
            // given
            String expression = "result = False";
            when(pythonEvaluator.evaluate(anyString(), anyMap())).thenReturn(false);
            Map<String, Object> body = doc(paragraph(
                    text("pre "),
                    pythonInline(expression, text("HIDDEN_PY_INLINE")),
                    text(" post")));

            // when
            Map<String, Object> result = generate(body, Map.of());

            // then
            assertThat(contentOf(firstChild(result))).containsExactly(text("pre "), text(" post"));
        }

        @Test
        @DisplayName("generate treats a python exception as a false condition (fail-closed)")
        void generate_givenPythonEvaluatorThrows_treatsConditionAsFalse() {
            // given — таймаут/синтаксическая ошибка не должны валить генерацию
            when(pythonEvaluator.evaluate(anyString(), anyMap()))
                    .thenThrow(new ScriptExecutionException("Python script timed out after 2 seconds"));
            Map<String, Object> body = doc(
                    pythonBlock("result = undefined_name", paragraph(text("HIDDEN_PY_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = generate(body, Map.of());

            // then
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }

        @Test
        @DisplayName("generate treats a generic python-engine failure as a false condition (fail-closed)")
        void generate_givenPythonEngineFailure_treatsConditionAsFalse() {
            // given — недоступный Python-движок (не ошибка скрипта) тоже не должен
            // валить генерацию: любая RuntimeException считается ложью
            when(pythonEvaluator.evaluate(anyString(), anyMap()))
                    .thenThrow(new IllegalStateException("engine unavailable"));
            Map<String, Object> body = doc(
                    pythonBlock("result = True", paragraph(text("HIDDEN_PY_TEXT"))),
                    paragraph(text("VISIBLE_TEXT")));

            // when
            Map<String, Object> result = generate(body, Map.of());

            // then — блок отброшен, генерация не упала
            assertThat(contentOf(result)).containsExactly(paragraph(text("VISIBLE_TEXT")));
            assertThat(textsIn(result)).containsExactly("VISIBLE_TEXT");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> generate(Map<String, Object> body, Map<String, Object> answers) {
        return generate(Template.builder().body(body).build(), answers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generate(Template template, Map<String, Object> answers) {
        return (Map<String, Object>) generator.generate(template, answers);
    }

    private static Template templateWithPhrasings(
            Map<String, Object> body, List<Map<String, Object>> variantContent) {
        PhrasingVariant variant = PhrasingVariant.builder()
                .id("v1")
                .content(variantContent)
                .build();
        Phrasing phrasing = Phrasing.builder()
                .id("ph1")
                .name("Формулировки")
                .variants(List.of(variant))
                .build();
        return Template.builder()
                .body(body)
                .phrasings(List.of(phrasing))
                .build();
    }

    /** Шаблон с одной формулировкой {@code ph1} и произвольным списком вариантов. */
    private static Template templateWithVariantList(
            Map<String, Object> body, List<PhrasingVariant> variants) {
        Phrasing phrasing = Phrasing.builder()
                .id("ph1")
                .name("Формулировки")
                .variants(variants)
                .build();
        return Template.builder()
                .body(body)
                .phrasings(List.of(phrasing))
                .build();
    }

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

    private static Map<String, Object> phrasingsRef(String id) {
        Map<String, Object> node = base("phrasingsRef");
        node.put("attrs", Map.of("phrasingsIds", List.of(id)));
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
        return conditionalBlockWithCondition(pythonCondition(expression), children);
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

    /** Условный блок без ключа "condition" в attrs — «показывать всегда». */
    private static Map<String, Object> blockWithoutCondition(Object... children) {
        Map<String, Object> node = base("conditionalBlock");
        node.put("attrs", new LinkedHashMap<>(Map.of("label", "Блок без условия")));
        node.put("content", new ArrayList<>(List.of(children)));
        return node;
    }

    private static Map<String, Object> conditionalInline(String expression, Object... inlines) {
        return conditionalInlineWithCondition(simpleCondition(expression), inlines);
    }

    private static Map<String, Object> pythonInline(String expression, Object... inlines) {
        return conditionalInlineWithCondition(pythonCondition(expression), inlines);
    }

    private static Map<String, Object> conditionalInlineWithCondition(
            Map<String, Object> condition, Object... inlines) {
        Map<String, Object> node = base("conditionalInline");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("condition", condition);
        attrs.put("label", "Условный текст");
        node.put("attrs", attrs);
        node.put("content", new ArrayList<>(List.of(inlines)));
        return node;
    }

    /** Условный фрагмент без ключа "condition" в attrs — «показывать всегда». */
    private static Map<String, Object> inlineWithoutCondition(Object... inlines) {
        Map<String, Object> node = base("conditionalInline");
        node.put("attrs", new LinkedHashMap<>(Map.of("label", "Условный текст без условия")));
        node.put("content", new ArrayList<>(List.of(inlines)));
        return node;
    }

    private static Map<String, Object> pythonCondition(String expression) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("type", "python");
        condition.put("expression", expression);
        return condition;
    }

    /** {@code expression == null} → attrs без ключа "condition" («условие не задано»). */
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

    /** Все типы узлов дерева в порядке обхода — удобно для «узел не остался». */
    private static List<String> typesIn(Map<String, Object> node) {
        List<String> types = new ArrayList<>();
        collectTypes(node, types);
        return types;
    }

    @SuppressWarnings("unchecked")
    private static void collectTypes(Map<String, Object> node, List<String> out) {
        if (node.get("type") instanceof String value) {
            out.add(value);
        }
        if (node.get("content") instanceof List<?> children) {
            for (Object child : children) {
                if (child instanceof Map<?, ?> childMap) {
                    collectTypes((Map<String, Object>) childMap, out);
                }
            }
        }
    }
}
