package com.docforge.template.model;

public enum ParameterType {
    TEXT,
    NUMBER,
    DATE,
    FIO,
    BOOLEAN,
    SELECT,
    /** Ссылка на запись из справочника. Ответ = entryId; суб-поля code__fieldKey подставляются в тело. */
    DIRECTORY
}
