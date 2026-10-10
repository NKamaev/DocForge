package com.docforge.template;

import com.docforge.template.dto.DiffResult;
import com.docforge.template.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two template snapshots to auto-generate a human-readable
 * change description for version history.
 */
@Service
public class TemplateDiffService {

    public DiffResult diff(ParameterTypeSupplier previous, ParameterTypeSupplier current) {
        List<String> changes = new ArrayList<>();
        boolean same = true;

        // Parameters diff
        same &= diffParameters(previous.getParameters(), current.getParameters(), changes);

        // Body diff
        same &= diffBody(previous.getBody(), current.getBody(), changes);

        // Questionnaire diff
        same &= diffQuestionnaire(previous.getQuestionnaire(), current.getQuestionnaire(), changes);

        // Scripts diff
        same &= diffScripts(previous.getScripts(), current.getScripts(), changes);

        // Phrasings diff
        same &= diffPhrasings(previous.getPhrasings(), current.getPhrasings(), changes);

        String summary;
        if (same) {
            summary = "Без изменений";
        } else if (changes.size() <= 3) {
            summary = String.join("; ", changes);
        } else {
            summary = changes.size() + " изменений";
        }

        return new DiffResult(summary, changes, !same);
    }

    private boolean diffParameters(List<Parameter> prev, List<Parameter> curr, List<String> out) {
        if (prev == null && curr == null) return true;
        if (prev == null) { out.add("Добавлены параметры"); return false; }
        if (curr == null) { out.add("Удалены все параметры"); return false; }

        Set<String> prevCodes = prev.stream().map(Parameter::getCode).collect(Collectors.toSet());
        Set<String> currCodes = curr.stream().map(Parameter::getCode).collect(Collectors.toSet());

        for (Parameter p : curr) {
            if (!prevCodes.contains(p.getCode())) {
                out.add("Добавлен параметр: " + p.getLabel());
            }
        }
        for (Parameter p : prev) {
            if (!currCodes.contains(p.getCode())) {
                out.add("Удалён параметр: " + p.getLabel());
            }
        }
        // Check for type/label changes
        Map<String, Parameter> prevMap = prev.stream().collect(Collectors.toMap(Parameter::getCode, p -> p));
        for (Parameter p : curr) {
            Parameter pp = prevMap.get(p.getCode());
            if (pp != null && !pp.getType().equals(p.getType())) {
                out.add("Изменён тип параметра: " + p.getLabel());
            }
        }
        return prevCodes.equals(currCodes);
    }

    private boolean diffBody(Map<String, Object> prev, Map<String, Object> curr, List<String> out) {
        if (prev == null && curr == null) return true;
        if (prev == null) { out.add("Добавлено тело шаблона"); return false; }
        if (curr == null) { out.add("Удалено тело шаблона"); return false; }
        if (!Objects.deepEquals(prev, curr)) {
            out.add("Внесены правки в текст шаблона");
            return false;
        }
        return true;
    }

    private boolean diffQuestionnaire(Questionnaire prev, Questionnaire curr, List<String> out) {
        if (prev == null && curr == null) return true;
        if (prev == null) { out.add("Добавлен опросный лист"); return false; }
        if (curr == null) { out.add("Удалён опросный лист"); return false; }

        Set<String> prevQ = collectQuestionIds(prev);
        Set<String> currQ = collectQuestionIds(curr);

        for (String id : currQ) {
            if (!prevQ.contains(id)) out.add("Добавлен вопрос: " + id);
        }
        for (String id : prevQ) {
            if (!currQ.contains(id)) out.add("Удалён вопрос: " + id);
        }
        return prevQ.equals(currQ);
    }

    private Set<String> collectQuestionIds(Questionnaire q) {
        Set<String> ids = new HashSet<>();
        if (q.getPages() != null) {
            for (QuestionnairePage p : q.getPages()) {
                if (p.getQuestions() != null) {
                    for (Question qq : p.getQuestions()) {
                        ids.add(qq.getId());
                    }
                }
            }
        }
        return ids;
    }

    private boolean diffScripts(List<Script> prev, List<Script> curr, List<String> out) {
        if (prev == null && curr == null) return true;
        if (prev == null) { out.add("Добавлены скрипты"); return false; }
        if (curr == null) { out.add("Удалены все скрипты"); return false; }

        Set<String> prevIds = prev.stream().map(Script::getId).collect(Collectors.toSet());
        Set<String> currIds = curr.stream().map(Script::getId).collect(Collectors.toSet());

        for (Script s : curr) {
            if (!prevIds.contains(s.getId())) out.add("Добавлен скрипт: " + s.getName());
        }
        for (Script s : prev) {
            if (!currIds.contains(s.getId())) out.add("Удалён скрипт: " + s.getName());
        }
        return prevIds.equals(currIds);
    }

    private boolean diffPhrasings(List<Phrasing> prev, List<Phrasing> curr, List<String> out) {
        if (prev == null && curr == null) return true;
        if (prev == null) { out.add("Добавлены формулировки"); return false; }
        if (curr == null) { out.add("Удалены все формулировки"); return false; }

        Set<String> prevIds = prev.stream().map(Phrasing::getId).collect(Collectors.toSet());
        Set<String> currIds = curr.stream().map(Phrasing::getId).collect(Collectors.toSet());

        for (Phrasing p : curr) {
            if (!prevIds.contains(p.getId())) out.add("Добавлена формулировка: " + p.getName());
        }
        for (Phrasing p : prev) {
            if (!currIds.contains(p.getId())) out.add("Удалена формулировка: " + p.getName());
        }
        return prevIds.equals(currIds);
    }

}
