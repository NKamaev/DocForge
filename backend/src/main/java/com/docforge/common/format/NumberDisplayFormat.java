package com.docforge.common.format;

public enum NumberDisplayFormat {
    /** 1 000 000 (default) */
    DEFAULT,
    /** один миллион */
    WORDS,
    /** 1 000 000 (один миллион) */
    WORDS_WITH_NUMBER,
    /** 1 000 000 штук */
    WITH_UNIT,
    /** один миллион рублей */
    WORDS_WITH_UNIT,
    /** 1 000 000 (один миллион) рублей */
    WORDS_WITH_NUMBER_AND_UNIT,
    /** 50% */
    PERCENTAGE
}
