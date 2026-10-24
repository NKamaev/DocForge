package com.docforge.document.generator;

import com.docforge.common.exception.ValidationException;
import com.docforge.common.format.*;
import com.docforge.template.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central document-generation pipeline.
 *
 * <p>Takes a {@link Template} and a map of questionnaire answers and produces
 * a resolved TipTap JSON tree with:
 * <ol>
 *   <li>All required answers validated (type + constraints).</li>
 *   <li>Conditional blocks evaluated and filtered.</li>
 *   <li>Parameter fields replaced with formatted text nodes.</li>
 * </ol>
 *
 * <p>The returned {@code Object} is a {@code Map<String, Object>} matching the
 * TipTap/ProseMirror JSON schema and ready to be stored as {@code generated_body}.
 */
@Component
public class DocumentGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerator.class);

    private final ConditionEvaluator conditionEvaluator;
    private final PythonConditionEvaluator pythonEvaluator;
    private final TipTapTreeWalker treeWalker;
    private final ParameterSubstitutor substitutor;
    private final ConditionalNodeFilter conditionalNodeFilter;

    public DocumentGenerator(
            ConditionEvaluator conditionEvaluator,
            PythonConditionEvaluator pythonEvaluator,
            TipTapTreeWalker treeWalker,
            ParameterSubstitutor substitutor,
            ConditionalNodeFilter conditionalNodeFilter) {
        this.conditionEvaluator = conditionEvaluator;
        this.pythonEvaluator    = pythonEvaluator;
        this.treeWalker         = treeWalker;
        this.substitutor        = substitutor;
        this.conditionalNodeFilter = conditionalNodeFilter;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates the resolved document body.
     *
     * @param template the template (with body, parameters, questionnaire)
     * @param answers  map of parameterCode → raw answer value
     * @return resolved TipTap JSON ({@code Map<String, Object>})
     * @throws ValidationException if required answers are missing or invalid
     */
    @SuppressWarnings("unchecked")
    public Object generate(Template template, Map<String, Object> answers) {
        List<Parameter> params = template.getParameters() != null
                ? template.getParameters() : List.of();
        Map<String, Object> body = template.getBody();

        // ── Step 1: Validate answers ──────────────────────────────────────────
        validateAnswers(params, answers);

        // ── Step 2: Expand phrasingsRef ───────────────────────────────────────
        final List<Phrasing> phrasings = template.getPhrasings() != null
                ? template.getPhrasings() : List.of();

        Map<String, Object> expanded = treeWalker.transform(body, node -> {
            // ── phrasingsRef → раскрыть в контент подходящих вариантов ──────
            if ("phrasingsRef".equals(String.valueOf(node.get("type")))) {
                Map<String, Object> attrs = getAttrs(node);
                List<String> ids = getPhrasingsIdsList(attrs);
                if (ids.isEmpty()) return List.of();

                String separator = attrs.containsKey("separator")
                        ? String.valueOf(attrs.get("separator")) : ", ";

                List<Map<String, Object>> result = new ArrayList<>();
                boolean first = true;
                for (String phrasingsId : ids) {
                    Phrasing phrasing = phrasings.stream()
                            .filter(p -> phrasingsId.equals(p.getId()))
                            .findFirst().orElse(null);
                    if (phrasing == null || phrasing.getVariants() == null || phrasing.getVariants().isEmpty()) {
                        log.debug("phrasingsRef '{}' not found or has no variants", phrasingsId);
                        continue;
                    }

                    // Первый вариант с выполненным условием (null = умолчание)
                    PhrasingVariant variant = phrasing.getVariants().stream()
                            .filter(v -> evalPhrasingCondition(v.getCondition(), answers))
                            .findFirst().orElse(null);
                    if (variant == null || variant.getContent() == null || variant.getContent().isEmpty()) continue;

                    if (!first) {
                        result.add(makeSeparatorNode(separator));
                    }
                    result.addAll(variant.getContent());
                    first = false;
                    log.debug("Expanding phrasingsRef '{}' → variant '{}'", phrasingsId, variant.getId());
                }
                return result;
            }

            return List.of(node);
        });

        // ── Step 3: Filter conditional nodes ─────────────────────────────────
        // Единая семантика условий с экспортом (BUG-064): ConditionalNodeFilter
        // обрабатывает conditionalBlock/conditionalInline/numberedClause, включая
        // узлы внутри развёрнутых вариантов фразеологизмов, и трактует отсутствие
        // условия как «показывать всегда».
        Map<String, Object> filtered = conditionalNodeFilter.filter(expanded, answers);

        // ── Step 4: Substitute parameter fields ───────────────────────────────
        Map<String, Parameter> paramIndex = params.stream()
                .collect(Collectors.toMap(Parameter::getCode, p -> p));

        return treeWalker.transform(filtered, node -> {
            if (!"parameterField".equals(node.get("type"))) return List.of(node);

            Map<String, Object> attrs = getAttrs(node);
            String code   = (String) attrs.get("parameterCode");
            Parameter param = paramIndex.get(code);
            Object value  = answers != null ? answers.get(code) : null;
            ParameterFormat formatOverride = parseFormat((Map<String, Object>) attrs.get("format"));
            String text   = substitutor.format(value, param, formatOverride);

            log.debug("Substituting parameterField '{}' → '{}'", code, text);
            return List.of(textNode(text));
        });
    }

    // ── Step 1: Validation ────────────────────────────────────────────────────

    private void validateAnswers(List<Parameter> params, Map<String, Object> answers) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (Parameter param : params) {
            Object value = answers != null ? answers.get(param.getCode()) : null;

            // Required check
            if (param.isRequired() && isEmpty(value)) {
                errors.put(param.getCode(), "Поле обязательно для заполнения");
                continue;
            }

            if (!isEmpty(value)) {
                // Type check
                String typeError = validateType(param, value);
                if (typeError != null) { errors.put(param.getCode(), typeError); continue; }

                // Constraint check (min/max/pattern)
                String constraintError = validateConstraints(param, value);
                if (constraintError != null) errors.put(param.getCode(), constraintError);
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Ошибка валидации ответов", errors);
        }
    }

    private String validateType(Parameter param, Object value) {
        return switch (param.getType()) {
            case NUMBER -> {
                if (value instanceof Number) yield null;
                try { Double.parseDouble(String.valueOf(value)); yield null; }
                catch (NumberFormatException e) { yield "Ожидается число"; }
            }
            case DATE -> {
                try { LocalDate.parse(String.valueOf(value).trim()); yield null; }
                catch (DateTimeParseException e) { yield "Ожидается дата в формате ГГГГ-ММ-ДД"; }
            }
            case BOOLEAN -> {
                if (value instanceof Boolean) yield null;
                String s = String.valueOf(value).trim().toLowerCase();
                yield (s.equals("true") || s.equals("false")) ? null : "Ожидается булево значение";
            }
            case SELECT -> {
                List<String> options = param.getOptions();
                if (options != null && !options.isEmpty()) {
                    String s = String.valueOf(value).trim();
                    yield options.contains(s) ? null : "Недопустимое значение: " + s;
                }
                yield null;
            }
            case DIRECTORY -> null; // entryId — any non-blank string is valid; sub-fields come from frontend
            default -> null; // TEXT, FIO — any non-blank string is valid
        };
    }

    private String validateConstraints(Parameter param, Object value) {
        ParameterValidation v = param.getValidation();
        if (v == null) return null;

        return switch (param.getType()) {
            case NUMBER -> {
                double d = toDouble(value);
                if (v.getMin() != null && d < v.getMin())
                    yield "Значение не может быть меньше " + formatBound(v.getMin());
                if (v.getMax() != null && d > v.getMax())
                    yield "Значение не может быть больше " + formatBound(v.getMax());
                yield null;
            }
            case TEXT, FIO -> {
                if (v.getPattern() != null
                        && !String.valueOf(value).matches(v.getPattern()))
                    yield "Значение не соответствует требуемому формату";
                yield null;
            }
            default -> null;
        };
    }

    private String formatBound(double bound) {
        return (bound == Math.floor(bound)) ? String.valueOf((long) bound) : String.valueOf(bound);
    }

    // ── Condition evaluation ──────────────────────────────────────────────────

    /**
     * Вычисляет условие, хранящееся в объекте {@link Condition} (из PhrasingVariant).
     * null-условие = вариант-умолчание → всегда true.
     */
    private boolean evalPhrasingCondition(Condition condition, Map<String, Object> answers) {
        if (condition == null) return true;
        String expression = condition.getExpression();
        if (expression == null || expression.isBlank()) return true;

        if (condition.getType() == ConditionType.PYTHON) {
            try {
                return pythonEvaluator.evaluate(expression, answers);
            } catch (Exception e) {
                log.warn("Python phrasing condition failed (treating as false): {}", e.getMessage());
                return false;
            }
        }
        return conditionEvaluator.evaluate(expression, answers);
    }

    // ── Node helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> getAttrs(Map<String, Object> node) {
        Object attrs = node.get("attrs");
        return attrs instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private Map<String, Object> textNode(String text) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    /** Читает phrasingsIds (новый формат) или fallback на legacy phrasingsId. */
    @SuppressWarnings("unchecked")
    private List<String> getPhrasingsIdsList(Map<String, Object> attrs) {
        Object ids = attrs.get("phrasingsIds");
        if (ids instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        // Legacy fallback
        Object single = attrs.get("phrasingsId");
        return single != null ? List.of(String.valueOf(single)) : List.of();
    }

    /** Преобразует строку-разделитель в TipTap-ноду (text или hardBreak). */
    private Map<String, Object> makeSeparatorNode(String separator) {
        if ("\\n".equals(separator) || "\n".equals(separator)) {
            return Map.of("type", "hardBreak");
        }
        return textNode(separator);
    }

    // ── Generic helpers ───────────────────────────────────────────────────────

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value).trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    /**
     * Десериализует Map из attrs.format в {@link ParameterFormat}.
     * Возвращает null если map пустая или не задана.
     */
    @SuppressWarnings("unchecked")
    private ParameterFormat parseFormat(Map<String, Object> formatMap) {
        if (formatMap == null || formatMap.isEmpty()) return null;
        ParameterFormat.ParameterFormatBuilder builder = ParameterFormat.builder();
        tryParseEnum(formatMap, "dateFormat",       DateFormat.class,         e -> builder.dateFormat(e));
        tryParseEnum(formatMap, "numberFormat",     NumberDisplayFormat.class, e -> builder.numberFormat(e));
        tryParseEnum(formatMap, "fioFormat",        FioDisplayFormat.class,   e -> builder.fioFormat(e));
        tryParseEnum(formatMap, "grammaticalCase",  GrammaticalCase.class,    e -> builder.grammaticalCase(e));
        Object unit = formatMap.get("unit");
        if (unit != null) builder.unit(String.valueOf(unit));
        return builder.build();
    }

    private <E extends Enum<E>> void tryParseEnum(
            Map<String, Object> map, String key, Class<E> enumClass, java.util.function.Consumer<E> setter) {
        Object val = map.get(key);
        if (val == null) return;
        try { setter.accept(Enum.valueOf(enumClass, String.valueOf(val).toUpperCase())); }
        catch (IllegalArgumentException ignored) { log.warn("Unknown {} value: {}", key, val); }
    }
}
