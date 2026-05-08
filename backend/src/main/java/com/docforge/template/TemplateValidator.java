package com.docforge.template;

import com.docforge.document.generator.TipTapTreeWalker;
import com.docforge.template.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates structural integrity of a template:
 * <ul>
 *   <li>All parameter_field nodes in body reference defined parameter codes.</li>
 *   <li>All conditional_block nodes with type=simple have a non-empty expression.</li>
 *   <li>All conditional_block nodes with type=python reference an existing script id.</li>
 *   <li>All questionnaire questions reference defined parameter codes.</li>
 *   <li>WARN: parameters not linked to any questionnaire question.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TemplateValidator {

    private final TipTapTreeWalker treeWalker;

    public TemplateValidationResult validate(Template template) {
        List<String> errors = new ArrayList<>();

        Set<String> paramCodes = template.getParameters() == null
                ? Set.of()
                : template.getParameters().stream()
                        .map(Parameter::getCode)
                        .collect(Collectors.toSet());

        Set<String> scriptIds = template.getScripts() == null
                ? Set.of()
                : template.getScripts().stream()
                        .map(Script::getId)
                        .collect(Collectors.toSet());

        // ── Validate body nodes ────────────────────────────────────────────────
        if (template.getBody() != null) {
            treeWalker.transform(template.getBody(), node -> {
                String type = (String) node.get("type");
                if ("parameterField".equals(type) || "parameter_field".equals(type)) {
                    String code = extractAttr(node, "parameterCode");
                    if (code == null) code = extractAttr(node, "parameter_code");
                    if (code != null && !code.isBlank() && !paramCodes.contains(code)) {
                        errors.add("Параметр '" + code + "' в теле шаблона не определён в списке параметров");
                    }
                } else if ("conditionalBlock".equals(type) || "conditional_block".equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attrs = (Map<String, Object>) node.get("attrs");
                    if (attrs != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> condition = (Map<String, Object>) attrs.get("condition");
                        if (condition != null) {
                            String condType = (String) condition.get("type");
                            if ("simple".equalsIgnoreCase(condType)) {
                                String expr = (String) condition.get("expression");
                                if (expr == null || expr.isBlank()) {
                                    String label = attrs.get("label") != null
                                            ? attrs.get("label").toString() : "без метки";
                                    errors.add("Условный блок '" + label + "' (type=simple) имеет пустое выражение");
                                }
                            } else if ("python".equalsIgnoreCase(condType)) {
                                String scriptId = (String) condition.get("scriptId");
                                if (scriptId == null) scriptId = (String) condition.get("script_id");
                                if (scriptId != null && !scriptId.isBlank() && !scriptIds.contains(scriptId)) {
                                    errors.add("Условный блок ссылается на несуществующий скрипт '" + scriptId + "'");
                                }
                            }
                        }
                    }
                }
                return List.of(node);
            });
        }

        // ── Validate questionnaire ─────────────────────────────────────────────
        Set<String> questionParamCodes = new HashSet<>();
        if (template.getQuestionnaire() != null
                && template.getQuestionnaire().getPages() != null) {
            for (QuestionnairePage page : template.getQuestionnaire().getPages()) {
                if (page.getQuestions() == null) continue;
                for (Question q : page.getQuestions()) {
                    String code = q.getParameterCode();
                    if (code != null) {
                        questionParamCodes.add(code);
                        if (!paramCodes.contains(code)) {
                            errors.add("Вопрос '" + q.getId() + "' привязан к несуществующему параметру '" + code + "'");
                        }
                    }
                }
            }
        }

        // ── WARN: unreferenced parameters ─────────────────────────────────────
        for (String code : paramCodes) {
            if (!questionParamCodes.contains(code)) {
                errors.add("WARN: параметр '" + code + "' не привязан ни к одному вопросу опросного листа");
            }
        }

        boolean valid = errors.stream().noneMatch(e -> !e.startsWith("WARN:"));
        return new TemplateValidationResult(valid, errors);
    }

    @SuppressWarnings("unchecked")
    private String extractAttr(Map<String, Object> node, String attrName) {
        Object attrs = node.get("attrs");
        if (attrs instanceof Map<?, ?> attrsMap) {
            Object val = ((Map<String, Object>) attrsMap).get(attrName);
            return val != null ? val.toString() : null;
        }
        return null;
    }
}
