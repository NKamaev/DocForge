package com.docforge.document.generator;

import com.docforge.common.format.*;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.ParameterType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Formats raw questionnaire answer values into human-readable Russian strings
 * for substitution into document text.
 *
 * <p>Effective format resolution order (highest priority first):
 * <ol>
 *   <li>{@code formatOverride} — per-node override from {@code parameterField.attrs.format}.</li>
 *   <li>{@code parameter.getFormat()} — default set on the parameter definition.</li>
 *   <li>System defaults (DATE → LONG_RU, NUMBER → DEFAULT, FIO → FULL / NOMINATIVE).</li>
 * </ol>
 *
 * <p>System defaults (no format configured):
 * <ul>
 *   <li>DATE   → «20 марта 2026 г.» (LONG_RU)</li>
 *   <li>NUMBER → «1 000 000» (DEFAULT)</li>
 *   <li>BOOLEAN → «Да» / «Нет»</li>
 *   <li>FIO → FULL / NOMINATIVE</li>
 *   <li>TEXT, SELECT, DIRECTORY → trimmed string as-is</li>
 *   <li>null / blank → «—» (em dash)</li>
 * </ul>
 */
@Component
public class ParameterSubstitutor {

    private static final String EMPTY_PLACEHOLDER = "—";
    private static final Locale RU = Locale.forLanguageTag("ru");

    /**
     * Russian genitive month names (months are 1-indexed).
     * Explicit lookup to guarantee the genitive form used in legal texts.
     */
    private static final String[] MONTHS_GENITIVE = {
        "", "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    private static final String[] MONTHS_NOMINATIVE = {
        "", "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
    };

    private final NumberToWordsService numberToWords;
    private final FioFormatterService  fioFormatter;

    public ParameterSubstitutor(NumberToWordsService numberToWords, FioFormatterService fioFormatter) {
        this.numberToWords = numberToWords;
        this.fioFormatter  = fioFormatter;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Delegates to {@link #format(Object, Parameter, ParameterFormat)} with no override.
     * Kept for callers that do not have a per-node format.
     */
    public String format(Object value, Parameter parameter) {
        return format(value, parameter, null);
    }

    /**
     * Formats {@code value} according to the effective format (override → param default → system).
     *
     * @param value          raw answer value (String, Number, Boolean, or null)
     * @param parameter      the parameter definition; may be null
     * @param formatOverride per-node format override from TipTap attrs; may be null
     * @return formatted string, never null
     */
    public String format(Object value, Parameter parameter, @Nullable ParameterFormat formatOverride) {
        if (isEmpty(value)) return EMPTY_PLACEHOLDER;
        if (parameter == null || parameter.getType() == null) {
            return String.valueOf(value).trim();
        }

        ParameterFormat effective = effectiveFormat(parameter, formatOverride);

        return switch (parameter.getType()) {
            case DATE    -> formatDate(value, effective);
            case NUMBER  -> formatNumber(value, effective);
            case BOOLEAN -> formatBoolean(value);
            case FIO     -> formatFio(value, effective);
            case TEXT, SELECT, DIRECTORY -> String.valueOf(value).trim();
        };
    }

    // ── Format resolution ─────────────────────────────────────────────────────

    private ParameterFormat effectiveFormat(Parameter parameter, ParameterFormat override) {
        if (override != null) return override;
        if (parameter.getFormat() != null) return parameter.getFormat();
        return ParameterFormat.builder().build(); // system defaults — all nulls
    }

    // ── DATE ──────────────────────────────────────────────────────────────────

    private String formatDate(Object value, ParameterFormat fmt) {
        String raw = String.valueOf(value).trim();
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return raw;
        }

        DateFormat df = fmt.getDateFormat() != null ? fmt.getDateFormat() : DateFormat.LONG_RU;

        return switch (df) {
            case DOTS        -> String.format("%02d.%02d.%04d",
                                    date.getDayOfMonth(), date.getMonthValue(), date.getYear());
            case LONG_RU     -> date.getDayOfMonth()
                                    + " " + MONTHS_GENITIVE[date.getMonthValue()]
                                    + " " + date.getYear() + " г.";
            case LONG_RU_GENITIVE -> longDateGenitive(date);
            case SHORT       -> String.format("%02d.%02d.%02d",
                                    date.getDayOfMonth(), date.getMonthValue(), date.getYear() % 100);
            case YEAR_ONLY   -> String.valueOf(date.getYear());
            case MONTH_YEAR  -> MONTHS_NOMINATIVE[date.getMonthValue()] + " " + date.getYear();
            case ISO         -> date.toString();
        };
    }

    private String longDateGenitive(LocalDate date) {
        String day  = ordinalGenitive(date.getDayOfMonth());
        String mon  = monthGenitiveAdj(date.getMonthValue());
        String year = ordinalGenitive(date.getYear());
        return day + " " + mon + " " + year + " года";
    }

    /** Порядковое числительное в родительном падеже для дня/года. */
    private String ordinalGenitive(int n) {
        return numberToWords.toWords(n, "") + "ого";
    }

    /** Название месяца в форме прилагательного (для длинной записи даты). */
    private String monthGenitiveAdj(int month) {
        return switch (month) {
            case 1  -> "январского";
            case 2  -> "февральского";
            case 3  -> "мартовского";
            case 4  -> "апрельского";
            case 5  -> "майского";
            case 6  -> "июньского";
            case 7  -> "июльского";
            case 8  -> "августовского";
            case 9  -> "сентябрьского";
            case 10 -> "октябрьского";
            case 11 -> "ноябрьского";
            case 12 -> "декабрьского";
            default -> "";
        };
    }

    // ── NUMBER ────────────────────────────────────────────────────────────────

    private String formatNumber(Object value, ParameterFormat fmt) {
        double d;
        if (value instanceof Number n) {
            d = n.doubleValue();
        } else {
            try {
                d = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return String.valueOf(value);
            }
        }

        NumberDisplayFormat nf  = fmt.getNumberFormat() != null ? fmt.getNumberFormat() : NumberDisplayFormat.DEFAULT;
        String rawUnit          = fmt.getUnit() != null ? fmt.getUnit().trim() : "";
        long   longVal          = (long) d;
        boolean isWhole         = (d == Math.floor(d) && !Double.isInfinite(d));
        String unit             = rawUnit.isEmpty() ? "" : pluralizeUnit(longVal, rawUnit);

        return switch (nf) {
            case DEFAULT -> defaultFormatNumber(d);
            case WORDS   -> isWhole
                    ? numberToWords.toWords(longVal, "")
                    : defaultFormatNumber(d); // дробные прописью не поддерживаем
            case WORDS_WITH_NUMBER -> isWhole
                    ? numberToWords.toWordsWithNumber(longVal, "")
                    : defaultFormatNumber(d);
            case WITH_UNIT -> defaultFormatNumber(d) + (unit.isEmpty() ? "" : " " + unit);
            case WORDS_WITH_UNIT -> isWhole
                    ? numberToWords.toWords(longVal, unit)
                    : defaultFormatNumber(d) + (unit.isEmpty() ? "" : " " + unit);
            case WORDS_WITH_NUMBER_AND_UNIT -> isWhole
                    ? numberToWords.toWordsWithNumber(longVal, unit)
                    : defaultFormatNumber(d) + (unit.isEmpty() ? "" : " " + unit);
            case PERCENTAGE -> defaultFormatNumber(d) + "%";
        };
    }

    private String defaultFormatNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return NumberFormat.getIntegerInstance(RU).format((long) d);
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(RU);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(d);
    }

    /**
     * Выбирает нужную форму единицы измерения по числу.
     * Поддерживает формат «штука|штуки|штук» (1|2-4|5+).
     * Если разделителей нет — возвращает строку как есть.
     */
    private String pluralizeUnit(long n, String unit) {
        String[] parts = unit.split("\\|", -1);
        if (parts.length < 3) return unit;
        String one  = parts[0];
        String few  = parts[1];
        String many = parts[2];
        long abs     = Math.abs(n);
        long mod100  = abs % 100;
        long mod10   = abs % 10;
        if (mod100 >= 11 && mod100 <= 19) return many;
        if (mod10 == 1)  return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }

    // ── FIO ───────────────────────────────────────────────────────────────────

    private String formatFio(Object value, ParameterFormat fmt) {
        FioDisplayFormat  fioFmt = fmt.getFioFormat()       != null ? fmt.getFioFormat()       : FioDisplayFormat.FULL;
        GrammaticalCase   cas    = fmt.getGrammaticalCase() != null ? fmt.getGrammaticalCase() : GrammaticalCase.NOMINATIVE;
        return fioFormatter.format(String.valueOf(value).trim(), fioFmt, cas);
    }

    // ── BOOLEAN ───────────────────────────────────────────────────────────────

    private String formatBoolean(Object value) {
        if (value instanceof Boolean b) return b ? "Да" : "Нет";
        return "true".equalsIgnoreCase(String.valueOf(value).trim()) ? "Да" : "Нет";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }
}
