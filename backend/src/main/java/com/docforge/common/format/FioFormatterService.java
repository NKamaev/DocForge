package com.docforge.common.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Форматирует ФИО по заданному стилю и падежу.
 *
 * <p>Ожидаемый входной формат: «Фамилия Имя Отчество» (2 или 3 слова).
 *
 * <p>Склонение реализовано по правилам русского языка для наиболее
 * распространённых окончаний. Нестандартные (иностранные, несклоняемые)
 * фамилии возвращаются без изменений.
 */
@Component
public class FioFormatterService {

    private static final Logger log = LoggerFactory.getLogger(FioFormatterService.class);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * @param fullFio         «Иванов Иван Иванович» (Фамилия Имя Отчество), не null
     * @param displayFormat   стиль отображения; null → FULL
     * @param grammaticalCase падеж; null → NOMINATIVE
     * @return отформатированная строка
     */
    public String format(String fullFio, FioDisplayFormat displayFormat, GrammaticalCase grammaticalCase) {
        if (fullFio == null || fullFio.isBlank()) return fullFio;

        FioDisplayFormat fmt  = displayFormat   != null ? displayFormat   : FioDisplayFormat.FULL;
        GrammaticalCase  cas  = grammaticalCase != null ? grammaticalCase : GrammaticalCase.NOMINATIVE;

        String[] parts = fullFio.trim().split("\\s+");
        String last  = parts.length > 0 ? capitalize(parts[0]) : "";
        String first = parts.length > 1 ? capitalize(parts[1]) : "";
        String patr  = parts.length > 2 ? capitalize(parts[2]) : "";

        // Определяем пол по отчеству; если отчества нет — по окончанию имени
        boolean male = detectMale(patr, first);

        // Склоняем каждую часть
        if (cas != GrammaticalCase.NOMINATIVE) {
            last  = declineSurname(last,  cas, male);
            first = declineFirstName(first, cas, male);
            patr  = declinePatronymic(patr, cas, male);
        }

        return switch (fmt) {
            case FULL       -> buildFull(last, first, patr);
            case INITIALS   -> buildInitials(last, first, patr);
            case FIRST_LAST -> (first + " " + last).trim();
            case LAST_ONLY  -> last;
            case FIRST_ONLY -> first;
        };
    }

    // ── Display builders ──────────────────────────────────────────────────────

    private String buildFull(String last, String first, String patr) {
        if (!patr.isEmpty()) return last + " " + first + " " + patr;
        if (!first.isEmpty()) return last + " " + first;
        return last;
    }

    private String buildInitials(String last, String first, String patr) {
        StringBuilder sb = new StringBuilder(last);
        if (!first.isEmpty()) {
            sb.append(" ").append(Character.toUpperCase(first.charAt(0))).append(".");
            if (!patr.isEmpty())
                sb.append(Character.toUpperCase(patr.charAt(0))).append(".");
        }
        return sb.toString();
    }

    // ── Gender detection ──────────────────────────────────────────────────────

    private boolean detectMale(String patronymic, String firstName) {
        if (!patronymic.isEmpty()) {
            String p = patronymic.toLowerCase();
            if (p.endsWith("ович") || p.endsWith("евич") || p.endsWith("ич")) return true;
            if (p.endsWith("овна") || p.endsWith("евна") || p.endsWith("ична") || p.endsWith("инична")) return false;
        }
        if (!firstName.isEmpty()) {
            String f = firstName.toLowerCase();
            // Мужские имена обычно оканчиваются на согласную или -й
            // Женские — на -а, -я (редко -ь)
            if (f.endsWith("а") || f.endsWith("я")) return false;
        }
        return true; // по умолчанию мужской
    }

    // ── Склонение фамилий ─────────────────────────────────────────────────────

    private String declineSurname(String name, GrammaticalCase cas, boolean male) {
        if (name.isEmpty()) return name;
        String low = name.toLowerCase();

        // Фамилии на -ский/-цкий/-ской (м) / -ская/-цкая (ж)
        if (low.endsWith("ский") || low.endsWith("цкий") || low.endsWith("зский")) {
            String stem = name.substring(0, name.length() - 2); // убираем ий
            return stem + adjectiveMaleSuffix(cas);
        }
        if (low.endsWith("ской")) {
            String stem = name.substring(0, name.length() - 2);
            return stem + adjectiveMaleSuffix(cas);
        }
        if (low.endsWith("ская") || low.endsWith("цкая")) {
            String stem = name.substring(0, name.length() - 2);
            return stem + adjectiveFemaleSuffix(cas);
        }
        // Фамилии на -ий/-ый (Толстой → Толстого)
        if ((low.endsWith("ий") || low.endsWith("ый")) && male) {
            String stem = name.substring(0, name.length() - 2);
            return stem + adjectiveMaleSuffix(cas);
        }
        // Фамилии на -ая/-яя (Толстая)
        if (low.endsWith("ая") || low.endsWith("яя")) {
            String stem = name.substring(0, name.length() - 2);
            return stem + adjectiveFemaleSuffix(cas);
        }
        // Фамилии на -ов/-ев/-ёв
        if (low.endsWith("ов") || low.endsWith("ев") || low.endsWith("ёв")) {
            if (male) return name + noun1Suffix(cas);
            // женские -ова/-ева не склоняем по образцу прилагательных, а по существительным
            return name;
        }
        if (low.endsWith("ова") || low.endsWith("ева") || low.endsWith("ёва")) {
            if (!male) return nounASuffix(name, name.length() - 1, cas);
            return name;
        }
        // Фамилии на -ин/-ын
        if (low.endsWith("ин") || low.endsWith("ын")) {
            if (male) return name + noun2Suffix(cas);
            return name;
        }
        if (low.endsWith("ина") || low.endsWith("ына")) {
            if (!male) return nounASuffix(name, name.length() - 1, cas);
            return name;
        }
        // Фамилии на -а/-я (Кочергина, Фоменко — не склоняем если -о/-е)
        if (low.endsWith("о") || low.endsWith("е") || low.endsWith("и") || low.endsWith("у")) {
            return name; // несклоняемые
        }
        if (low.endsWith("а")) {
            return nounASuffix(name, name.length() - 1, cas);
        }
        if (low.endsWith("я")) {
            return nounYaSuffix(name, name.length() - 1, cas);
        }
        // Фамилии на согласную (мужские)
        if (male) return name + noun1Suffix(cas);

        return name; // женские на согласную не склоняются
    }

    // ── Склонение имён ────────────────────────────────────────────────────────

    private String declineFirstName(String name, GrammaticalCase cas, boolean male) {
        if (name.isEmpty()) return name;
        String low = name.toLowerCase();

        if (male) {
            if (low.endsWith("й")) { // Андрей, Николай, Евгений
                String stem = name.substring(0, name.length() - 1);
                return stem + maleJSuffix(cas);
            }
            if (low.endsWith("ь")) { // Игорь, Илья — мягкий знак
                String stem = name.substring(0, name.length() - 1);
                return stem + maleSoftSuffix(cas);
            }
            if (low.endsWith("а")) { // Никита, Илья-stub
                return nounASuffix(name, name.length() - 1, cas);
            }
            if (low.endsWith("я")) {
                return nounYaSuffix(name, name.length() - 1, cas);
            }
            // Оканчивается на согласную: Иван, Пётр, Александр
            return name + noun1Suffix(cas);
        } else {
            // Женские имена
            if (low.endsWith("ья") || low.endsWith("ия")) { // Наталья, Мария
                String stem = name.substring(0, name.length() - 1);
                return stem + femaleIySuffix(cas);
            }
            if (low.endsWith("я")) { // Надя, Таня
                return nounYaSuffix(name, name.length() - 1, cas);
            }
            if (low.endsWith("а")) { // Анна, Ольга (но Ольга будет Ольге и т.д.)
                return nounASuffix(name, name.length() - 1, cas);
            }
            if (low.endsWith("ь")) { // Любовь
                return name; // несклоняемо в этой модели
            }
            return name;
        }
    }

    // ── Склонение отчеств ─────────────────────────────────────────────────────

    private String declinePatronymic(String name, GrammaticalCase cas, boolean male) {
        if (name.isEmpty()) return name;
        String low = name.toLowerCase();

        if (male) {
            // -ович/-евич
            if (low.endsWith("ович") || low.endsWith("евич")) {
                return name + noun1Suffix(cas);
            }
            if (low.endsWith("ич")) {
                return name + noun1Suffix(cas);
            }
        } else {
            // -овна/-евна
            if (low.endsWith("овна") || low.endsWith("евна") || low.endsWith("ична")) {
                return nounASuffix(name, name.length() - 1, cas);
            }
        }
        return name;
    }

    // ── Суффиксные таблицы ────────────────────────────────────────────────────

    /** Окончания для существительных 1-го склонения муж. рода (на согласную): Иван */
    private String noun1Suffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "";
            case GENITIVE      -> "а";
            case DATIVE        -> "у";
            case ACCUSATIVE    -> "а";
            case INSTRUMENTAL  -> "ом";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Окончания для существительных 2-го склонения муж. рода на -ин/-ын: Путин */
    private String noun2Suffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "";
            case GENITIVE      -> "а";
            case DATIVE        -> "у";
            case ACCUSATIVE    -> "а";
            case INSTRUMENTAL  -> "ым";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Склонение имён на -й: Андрей → Андрея, Андрею, … */
    private String maleJSuffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "й";
            case GENITIVE      -> "я";
            case DATIVE        -> "ю";
            case ACCUSATIVE    -> "я";
            case INSTRUMENTAL  -> "ем";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Склонение имён на мягкий знак: Игорь → Игоря, Игорю, … */
    private String maleSoftSuffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "ь";
            case GENITIVE      -> "я";
            case DATIVE        -> "ю";
            case ACCUSATIVE    -> "я";
            case INSTRUMENTAL  -> "ем";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Склонение имён на -ья/-ия (женские): Мария → Марии, Марии, Марию */
    private String femaleIySuffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "я";
            case GENITIVE      -> "и";
            case DATIVE        -> "и";
            case ACCUSATIVE    -> "ю";
            case INSTRUMENTAL  -> "ей";
            case PREPOSITIONAL -> "и";
        };
    }

    /** Склонение слов на -а: Иванова, Анна → убираем а, применяем суффикс */
    private String nounASuffix(String name, int stemEnd, GrammaticalCase cas) {
        String stem = name.substring(0, stemEnd);
        String last = stem.isEmpty() ? "" : stem.substring(stem.length() - 1).toLowerCase();
        // После шипящих/ц — особые окончания
        boolean hissing = "жшщчц".contains(last);
        return stem + switch (cas) {
            case NOMINATIVE    -> "а";
            case GENITIVE      -> "ы";
            case DATIVE        -> "е";
            case ACCUSATIVE    -> "у";
            case INSTRUMENTAL  -> hissing ? "ей" : "ой";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Склонение слов на -я: Надя → убираем я, применяем суффикс */
    private String nounYaSuffix(String name, int stemEnd, GrammaticalCase cas) {
        String stem = name.substring(0, stemEnd);
        return stem + switch (cas) {
            case NOMINATIVE    -> "я";
            case GENITIVE      -> "и";
            case DATIVE        -> "е";
            case ACCUSATIVE    -> "ю";
            case INSTRUMENTAL  -> "ей";
            case PREPOSITIONAL -> "е";
        };
    }

    /** Окончания прилагательных мужского рода: -ий → -ого/-ому и т.д. */
    private String adjectiveMaleSuffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "ий";
            case GENITIVE      -> "ого";
            case DATIVE        -> "ому";
            case ACCUSATIVE    -> "ого";
            case INSTRUMENTAL  -> "им";
            case PREPOSITIONAL -> "ом";
        };
    }

    /** Окончания прилагательных женского рода: -ая → -ой/-ой/-ую и т.д. */
    private String adjectiveFemaleSuffix(GrammaticalCase cas) {
        return switch (cas) {
            case NOMINATIVE    -> "ая";
            case GENITIVE      -> "ой";
            case DATIVE        -> "ой";
            case ACCUSATIVE    -> "ую";
            case INSTRUMENTAL  -> "ой";
            case PREPOSITIONAL -> "ой";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
