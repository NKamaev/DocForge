package com.docforge.template;

import com.docforge.template.model.Parameter;
import com.docforge.template.model.Phrasing;
import com.docforge.template.model.Questionnaire;
import com.docforge.template.model.Script;

import java.util.List;
import java.util.Map;

/**
 * Interface for accessing template content (works with both
 * TemplateSnapshot and live Template).
 */
public interface ParameterTypeSupplier {
    List<Parameter> getParameters();
    Map<String, Object> getBody();
    Questionnaire getQuestionnaire();
    List<Script> getScripts();
    List<Phrasing> getPhrasings();
}
