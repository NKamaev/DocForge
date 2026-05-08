package com.docforge.document.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates "simple" condition expressions against a map of answers.
 *
 * <p>Expression format (produced by the frontend ConditionBuilder):
 * <pre>
 *   has_penalty == true
 *   penalty_percent > 10
 *   has_penalty == true AND penalty_percent > 10
 *   contract_type in Подряд, Услуги OR status == active
 * </pre>
 *
 * <p>Supported operators: ==, !=, >, <, >=, <=, in, not_in, is_empty, is_not_empty
 * <p>Logical connectors: AND, OR (evaluated left-to-right, no precedence grouping)
 */
@Component
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    // Splits on AND / OR connectors surrounded by whitespace
    private static final Pattern CONNECTOR_PATTERN =
            Pattern.compile("\\s+(AND|OR)\\s+", Pattern.CASE_INSENSITIVE);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates a simple expression.
     *
     * @param expression condition string, e.g. {@code "has_penalty == true AND amount > 0"}
     * @param answers    parameter values from the questionnaire answers
     * @return {@code true} if the condition holds, {@code false} otherwise
     */
    public boolean evaluate(String expression, Map<String, Object> answers) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        List<Token> tokens = tokenize(expression);
        if (tokens.isEmpty()) return true;

        boolean result = evaluateClause(tokens.get(0).clause(), answers);
        for (int i = 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            boolean next = evaluateClause(t.clause(), answers);
            result = "OR".equalsIgnoreCase(t.connector()) ? (result || next) : (result && next);
        }
        return result;
    }

    // ── Tokenisation ──────────────────────────────────────────────────────────

    private record Token(String connector, String clause) {}

    /**
     * Splits the expression into a list of (connector, clause) pairs.
     * The first token has a {@code null} connector.
     */
    private List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = CONNECTOR_PATTERN.matcher(expression);

        int lastEnd = 0;
        String pendingConnector = null;

        while (m.find()) {
            String clause = expression.substring(lastEnd, m.start()).trim();
            if (!clause.isEmpty()) {
                tokens.add(new Token(pendingConnector, clause));
            }
            pendingConnector = m.group(1).toUpperCase();
            lastEnd = m.end();
        }

        String tail = expression.substring(lastEnd).trim();
        if (!tail.isEmpty()) {
            tokens.add(new Token(pendingConnector, tail));
        }

        return tokens;
    }

    // ── Clause evaluation ─────────────────────────────────────────────────────

    /**
     * Evaluates a single clause: {@code "paramCode operator [value]"}.
     *
     * <p>The value part is everything after the operator token, allowing
     * multi-word / comma-separated values for {@code in} / {@code not_in}.
     */
    private boolean evaluateClause(String clause, Map<String, Object> answers) {
        String[] parts = clause.strip().split("\\s+", 3);
        if (parts.length < 2) {
            log.warn("Unparseable clause (too few tokens): '{}'", clause);
            return false;
        }

        String paramCode = parts[0];
        String operator  = parts[1].toLowerCase();
        String rawValue  = parts.length == 3 ? parts[2] : "";

        Object answer = answers != null ? answers.get(paramCode) : null;

        return switch (operator) {
            case "is_empty"     -> isEmpty(answer);
            case "is_not_empty" -> !isEmpty(answer);
            case "in"           -> evalIn(answer, rawValue, true);
            case "not_in"       -> evalIn(answer, rawValue, false);
            case "==", "!="     -> evalEquality(answer, rawValue, "==".equals(operator));
            case ">", "<", ">=", "<=" -> evalComparison(answer, rawValue, operator);
            default -> {
                log.warn("Unknown operator '{}' in clause '{}'", operator, clause);
                yield false;
            }
        };
    }

    // ── Operators ─────────────────────────────────────────────────────────────

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    private boolean evalEquality(Object answer, String rawValue, boolean expectEqual) {
        if (answer == null) return false;
        Object parsed = parseScalar(rawValue, answer);
        boolean eq = toComparable(answer).equals(toComparable(parsed));
        return expectEqual == eq;
    }

    private boolean evalComparison(Object answer, String rawValue, String op) {
        if (answer == null) return false;
        Double left  = toDouble(answer);
        Double right = toDouble(rawValue);
        if (left == null || right == null) return false;
        return switch (op) {
            case ">"  -> left >  right;
            case "<"  -> left <  right;
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            default   -> false;
        };
    }

    /**
     * Evaluates {@code in} / {@code not_in}. The {@code rawValue} is a
     * comma-separated list, e.g. {@code "Подряд, Услуги"}.
     */
    private boolean evalIn(Object answer, String rawValue, boolean expectIn) {
        if (answer == null) return false;
        String answerStr = String.valueOf(answer).trim();
        List<String> options = Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        boolean found = options.contains(answerStr);
        return expectIn == found;
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    /**
     * Tries to convert rawValue to the same type as answer for a fair comparison.
     * Falls back to plain String if the answer type is not recognised.
     */
    private Object parseScalar(String raw, Object hint) {
        if (hint instanceof Boolean) {
            return Boolean.parseBoolean(raw);
        }
        if (hint instanceof Number) {
            try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        }
        return raw;
    }

    /**
     * Normalises a value to a {@link Comparable} for equality checks:
     * numbers → Double, booleans → Boolean, everything else → String.
     */
    private Comparable<Object> toComparable(Object value) {
        if (value instanceof Boolean b) return cast(b);
        if (value instanceof Number n)  return cast(n.doubleValue());
        return cast(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private <T> Comparable<Object> cast(T val) {
        return (Comparable<Object>) val;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
