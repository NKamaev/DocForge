package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Опросный лист шаблона — многостраничная форма для заполнения параметров. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {

    private String title;
    private List<QuestionnairePage> pages;
}
