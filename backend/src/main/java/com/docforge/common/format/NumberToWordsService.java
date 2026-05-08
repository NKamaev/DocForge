package com.docforge.common.format;

import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Преобразует целые числа в русские слова.
 * Поддерживает числа от 0 до 999 999 999 999.
 */
@Component
public class NumberToWordsService {

    private static final Locale RU = Locale.forLanguageTag("ru");

    private static final String[] ONES_M = {
        "", "один", "два", "три", "четыре", "пять",
        "шесть", "семь", "восемь", "девять"
    };
    private static final String[] ONES_F = {
        "", "одна", "две", "три", "четыре", "пять",
        "шесть", "семь", "восемь", "девять"
    };
    private static final String[] TEENS = {
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    };
    private static final String[] TENS = {
        "", "десять", "двадцать", "тридцать", "сорок", "пятьдесят",
        "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    };
    private static final String[] HUNDREDS = {
        "", "сто", "двести", "триста", "четыреста", "пятьсот",
        "шестьсот", "семьсот", "восемьсот", "девятьсот"
    };

    // [именительный ед., родительный ед., родительный мн.]
    private static final String[] THOUSANDS = {"тысяча", "тысячи", "тысяч"};
    private static final String[] MILLIONS   = {"миллион", "миллиона", "миллионов"};
    private static final String[] BILLIONS   = {"миллиард", "миллиарда", "миллиардов"};

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * «один миллион рублей», «двадцать пять штук».
     * Если unit пуст — только слова без суффикса.
     */
    public String toWords(long number, String unit) {
        String words = convert(number);
        return appendUnit(words, unit);
    }

    /**
     * «1 000 000 (один миллион) рублей».
     * Если unit пуст — «1 000 000 (один миллион)».
     */
    public String toWordsWithNumber(long number, String unit) {
        String formatted = NumberFormat.getIntegerInstance(RU).format(number);
        String words = convert(number);
        String result = formatted + " (" + words + ")";
        return appendUnit(result, unit);
    }

    // ── Core conversion ───────────────────────────────────────────────────────

    private String convert(long number) {
        if (number == 0) return "ноль";
        if (number < 0)  return "минус " + convert(-number);

        StringBuilder sb = new StringBuilder();

        long billions  = number / 1_000_000_000L;
        long millions  = (number % 1_000_000_000L) / 1_000_000L;
        long thousands = (number % 1_000_000L) / 1_000L;
        long remainder = number % 1_000L;

        if (billions > 0) {
            sb.append(hundredsToWords(billions, false)).append(" ")
              .append(pluralize(billions, BILLIONS)).append(" ");
        }
        if (millions > 0) {
            sb.append(hundredsToWords(millions, false)).append(" ")
              .append(pluralize(millions, MILLIONS)).append(" ");
        }
        if (thousands > 0) {
            sb.append(hundredsToWords(thousands, true)).append(" ")
              .append(pluralize(thousands, THOUSANDS)).append(" ");
        }
        if (remainder > 0) {
            sb.append(hundredsToWords(remainder, false));
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /** Преобразует число 1–999 в слова. feminine=true для тысяч (одна тысяча, две тысячи). */
    private String hundredsToWords(long n, boolean feminine) {
        StringBuilder sb = new StringBuilder();
        long h = n / 100;
        long rest = n % 100;

        if (h > 0) sb.append(HUNDREDS[(int) h]).append(" ");

        if (rest >= 10 && rest <= 19) {
            sb.append(TEENS[(int) (rest - 10)]);
        } else {
            long tens = rest / 10;
            long ones = rest % 10;
            if (tens > 0) sb.append(TENS[(int) tens]).append(" ");
            if (ones > 0) sb.append(feminine ? ONES_F[(int) ones] : ONES_M[(int) ones]);
        }

        return sb.toString().trim();
    }

    /** Склонение существительного по числу. Работает для 1/2-4/5+ */
    private String pluralize(long n, String[] forms) {
        long mod100 = n % 100;
        long mod10  = n % 10;
        if (mod100 >= 11 && mod100 <= 19) return forms[2];
        if (mod10 == 1)  return forms[0];
        if (mod10 >= 2 && mod10 <= 4) return forms[1];
        return forms[2];
    }

    private String appendUnit(String base, String unit) {
        if (unit == null || unit.isBlank()) return base;
        return base + " " + unit.trim();
    }
}
