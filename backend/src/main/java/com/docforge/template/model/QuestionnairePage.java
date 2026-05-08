package com.docforge.template.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Страница (шаг) опросного листа. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnairePage {

    private String title;
    private List<Question> questions;
}
