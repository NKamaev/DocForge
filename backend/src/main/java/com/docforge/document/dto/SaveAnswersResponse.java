package com.docforge.document.dto;

import java.util.Map;

public record SaveAnswersResponse(
        Map<String, Object> answers,
        QuestionnaireResponse questionnaire
) {}
