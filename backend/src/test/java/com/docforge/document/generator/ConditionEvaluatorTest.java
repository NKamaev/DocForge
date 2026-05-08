package com.docforge.document.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
    }

    // ── Boolean equality ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Boolean equality (==)")
    class BooleanEquality {

        @Test
        @DisplayName("has_penalty == true → true when has_penalty=true")
        void booleanTrue() {
            Map<String, Object> answers = Map.of("has_penalty", true);
            assertTrue(evaluator.evaluate("has_penalty == true", answers));
        }

        @Test
        @DisplayName("has_penalty == true → false when has_penalty=false")
        void booleanFalse() {
            Map<String, Object> answers = Map.of("has_penalty", false);
            assertFalse(evaluator.evaluate("has_penalty == true", answers));
        }

        @Test
        @DisplayName("has_penalty != true → true when has_penalty=false")
        void booleanNotEqual() {
            Map<String, Object> answers = Map.of("has_penalty", false);
            assertTrue(evaluator.evaluate("has_penalty != true", answers));
        }
    }

    // ── Numeric comparison ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Numeric comparison")
    class NumericComparison {

        @Test
        @DisplayName("penalty_percent > 10 → true when penalty_percent=15")
        void greaterThan() {
            Map<String, Object> answers = Map.of("penalty_percent", 15);
            assertTrue(evaluator.evaluate("penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("penalty_percent > 10 → false when penalty_percent=5")
        void greaterThanFalse() {
            Map<String, Object> answers = Map.of("penalty_percent", 5);
            assertFalse(evaluator.evaluate("penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("penalty_percent >= 10 → true when penalty_percent=10 (boundary)")
        void greaterOrEqual() {
            Map<String, Object> answers = Map.of("penalty_percent", 10);
            assertTrue(evaluator.evaluate("penalty_percent >= 10", answers));
        }

        @Test
        @DisplayName("penalty_percent < 10 → true when penalty_percent=3.5 (Double answer)")
        void lessThanDouble() {
            Map<String, Object> answers = Map.of("penalty_percent", 3.5);
            assertTrue(evaluator.evaluate("penalty_percent < 10", answers));
        }

        @Test
        @DisplayName("amount <= 100 → true when amount=100")
        void lessOrEqual() {
            Map<String, Object> answers = Map.of("amount", 100);
            assertTrue(evaluator.evaluate("amount <= 100", answers));
        }

        @Test
        @DisplayName("amount == 42 → true when answer is Integer 42")
        void equalNumericInteger() {
            Map<String, Object> answers = Map.of("amount", 42);
            assertTrue(evaluator.evaluate("amount == 42", answers));
        }
    }

    // ── AND / OR ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AND / OR connectors")
    class LogicalConnectors {

        @Test
        @DisplayName("AND: both true → true")
        void andBothTrue() {
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 15);
            assertTrue(evaluator.evaluate("has_penalty == true AND penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("AND: first false → false")
        void andFirstFalse() {
            Map<String, Object> answers = Map.of("has_penalty", false, "penalty_percent", 15);
            assertFalse(evaluator.evaluate("has_penalty == true AND penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("AND: second false → false")
        void andSecondFalse() {
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);
            assertFalse(evaluator.evaluate("has_penalty == true AND penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("OR: first true → true")
        void orFirstTrue() {
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 5);
            assertTrue(evaluator.evaluate("has_penalty == true OR penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("OR: both false → false")
        void orBothFalse() {
            Map<String, Object> answers = Map.of("has_penalty", false, "penalty_percent", 5);
            assertFalse(evaluator.evaluate("has_penalty == true OR penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("Three clauses: A AND B AND C — all true")
        void threeClauses() {
            Map<String, Object> answers = Map.of(
                    "has_penalty", true,
                    "penalty_percent", 15,
                    "amount", 1000
            );
            assertTrue(evaluator.evaluate(
                    "has_penalty == true AND penalty_percent > 10 AND amount > 500", answers));
        }

        @Test
        @DisplayName("case-insensitive: 'and' connector")
        void andLowercase() {
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 15);
            assertTrue(evaluator.evaluate("has_penalty == true and penalty_percent > 10", answers));
        }
    }

    // ── Null / missing parameter ──────────────────────────────────────────────

    @Nested
    @DisplayName("Null-safety")
    class NullSafety {

        @Test
        @DisplayName("null parameter → false for == comparison")
        void nullEquality() {
            Map<String, Object> answers = new HashMap<>();
            answers.put("has_penalty", null);
            assertFalse(evaluator.evaluate("has_penalty == true", answers));
        }

        @Test
        @DisplayName("missing parameter key → false")
        void missingKey() {
            Map<String, Object> answers = Map.of();
            assertFalse(evaluator.evaluate("has_penalty == true", answers));
        }

        @Test
        @DisplayName("null parameter → false for numeric comparison")
        void nullNumeric() {
            Map<String, Object> answers = new HashMap<>();
            answers.put("penalty_percent", null);
            assertFalse(evaluator.evaluate("penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("null answers map → false")
        void nullAnswers() {
            assertFalse(evaluator.evaluate("has_penalty == true", null));
        }
    }

    // ── is_empty / is_not_empty ───────────────────────────────────────────────

    @Nested
    @DisplayName("is_empty / is_not_empty")
    class EmptinessCheck {

        @Test
        @DisplayName("is_empty → true for null")
        void isEmptyNull() {
            Map<String, Object> answers = new HashMap<>();
            answers.put("note", null);
            assertTrue(evaluator.evaluate("note is_empty", answers));
        }

        @Test
        @DisplayName("is_empty → true for blank string")
        void isEmptyBlank() {
            Map<String, Object> answers = Map.of("note", "  ");
            assertTrue(evaluator.evaluate("note is_empty", answers));
        }

        @Test
        @DisplayName("is_empty → false for non-blank string")
        void isEmptyFalse() {
            Map<String, Object> answers = Map.of("note", "hello");
            assertFalse(evaluator.evaluate("note is_empty", answers));
        }

        @Test
        @DisplayName("is_not_empty → true for non-blank string")
        void isNotEmpty() {
            Map<String, Object> answers = Map.of("note", "hello");
            assertTrue(evaluator.evaluate("note is_not_empty", answers));
        }

        @Test
        @DisplayName("is_empty → true for missing key")
        void isEmptyMissingKey() {
            assertTrue(evaluator.evaluate("note is_empty", Map.of()));
        }
    }

    // ── in / not_in ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("in / not_in")
    class InOperator {

        @Test
        @DisplayName("in → true when value is in list")
        void inTrue() {
            Map<String, Object> answers = Map.of("contract_type", "Подряд");
            assertTrue(evaluator.evaluate("contract_type in Подряд, Услуги", answers));
        }

        @Test
        @DisplayName("in → false when value is not in list")
        void inFalse() {
            Map<String, Object> answers = Map.of("contract_type", "Аренда");
            assertFalse(evaluator.evaluate("contract_type in Подряд, Услуги", answers));
        }

        @Test
        @DisplayName("not_in → true when value is absent from list")
        void notIn() {
            Map<String, Object> answers = Map.of("contract_type", "Аренда");
            assertTrue(evaluator.evaluate("contract_type not_in Подряд, Услуги", answers));
        }

        @Test
        @DisplayName("in → false for null answer")
        void inNull() {
            Map<String, Object> answers = new HashMap<>();
            answers.put("contract_type", null);
            assertFalse(evaluator.evaluate("contract_type in Подряд, Услуги", answers));
        }
    }

    // ── Directory field keys (bank__field) ───────────────────────────────────

    @Nested
    @DisplayName("Directory fields with __ prefix (e.g. bank__is_active)")
    class DirectoryFields {

        @Test
        @DisplayName("bank__is_active == true → true when answer is Boolean true")
        void booleanFieldTrue() {
            Map<String, Object> answers = Map.of("bank__is_active", true);
            assertTrue(evaluator.evaluate("bank__is_active == true", answers));
        }

        @Test
        @DisplayName("bank__is_active == true → false when answer is Boolean false")
        void booleanFieldFalse() {
            Map<String, Object> answers = Map.of("bank__is_active", false);
            assertFalse(evaluator.evaluate("bank__is_active == true", answers));
        }

        @Test
        @DisplayName("bank__is_active == true → true when answer is String 'true'")
        void booleanFieldStringTrue() {
            Map<String, Object> answers = Map.of("bank__is_active", "true");
            assertTrue(evaluator.evaluate("bank__is_active == true", answers));
        }

        @Test
        @DisplayName("bank__type == ВТБ → true when answer matches")
        void selectFieldMatch() {
            Map<String, Object> answers = Map.of("bank__type", "ВТБ");
            assertTrue(evaluator.evaluate("bank__type == ВТБ", answers));
        }

        @Test
        @DisplayName("bank__type == ВТБ → false when answer differs")
        void selectFieldNoMatch() {
            Map<String, Object> answers = Map.of("bank__type", "Сбер");
            assertFalse(evaluator.evaluate("bank__type == ВТБ", answers));
        }

        @Test
        @DisplayName("bank__rating > 3 → true when rating=5")
        void numericFieldGreater() {
            Map<String, Object> answers = Map.of("bank__rating", 5);
            assertTrue(evaluator.evaluate("bank__rating > 3", answers));
        }

        @Test
        @DisplayName("bank__rating > 3 → false when rating=2")
        void numericFieldLess() {
            Map<String, Object> answers = Map.of("bank__rating", 2);
            assertFalse(evaluator.evaluate("bank__rating > 3", answers));
        }

        @Test
        @DisplayName("AND group: bank__is_active == true AND bank__type == ВТБ → true")
        void andGroupBothTrue() {
            Map<String, Object> answers = Map.of("bank__is_active", true, "bank__type", "ВТБ");
            assertTrue(evaluator.evaluate("bank__is_active == true AND bank__type == ВТБ", answers));
        }

        @Test
        @DisplayName("AND group: bank__is_active == true AND bank__type == ВТБ → false when type differs")
        void andGroupSecondFalse() {
            Map<String, Object> answers = Map.of("bank__is_active", true, "bank__type", "Сбер");
            assertFalse(evaluator.evaluate("bank__is_active == true AND bank__type == ВТБ", answers));
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("blank expression → true (no restriction)")
        void blankExpression() {
            assertTrue(evaluator.evaluate("", Map.of()));
            assertTrue(evaluator.evaluate("  ", Map.of()));
            assertTrue(evaluator.evaluate(null, Map.of()));
        }

        @Test
        @DisplayName("string equality")
        void stringEquality() {
            Map<String, Object> answers = Map.of("status", "active");
            assertTrue(evaluator.evaluate("status == active", answers));
            assertFalse(evaluator.evaluate("status == inactive", answers));
        }
    }
}
