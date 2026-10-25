package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.PageMargins;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class PdfExportServiceImpl implements PdfExportService {

    private static final String FONT_FAMILY = "Noto Serif";

    /**
     * Имена семейств (в нижнем регистре), которые обязаны резолвиться в приложенное
     * NotoSerif (BUG-077). Generic-имена CSS ({@code serif}, {@code sans-serif},
     * {@code monospace}) и base-14 имена openhtmltopdf ({@code serif} → Times-Roman,
     * {@code sans-serif} → Helvetica, {@code monospace} → Courier) не содержат
     * кириллицы: openhtmltopdf рисует недостающие глифы символом {@code #}.
     */
    private static final Set<String> BUNDLED_ALIASES = Set.of(
            "noto serif",
            "serif", "sans-serif", "sansserif", "monospace", "monospaced",
            "cursive", "fantasy", "dialog", "dialoginput",
            "times", "times roman", "timesroman", "timesnewroman", "times new roman", "times-roman",
            "helvetica", "courier", "symbol", "zapfdingbats");

    /**
     * Семейства, под которыми NotoSerif дополнительно регистрируется в MAIN-сторе
     * openhtmltopdf. Ключи должны совпадать с {@code FontUtil.normalizeFontFamily}:
     * lookup регистрозависим, поэтому generic-имена даются в нормализованном виде
     * ({@code serif} → {@code Serif}). Для прочих случаев (другой регистр, экзотические
     * имена из textStyle-меток) есть FALLBACK_PRE-регистрация — она срабатывает раньше
     * base-14 шрифтов.
     */
    private static final String[] FONT_ALIASES = {
            "Times New Roman", "Times", "TimesRoman", "Serif", "SansSerif", "Monospaced",
            "Helvetica", "Courier"
    };

    @Override
    public byte[] export(Map<String, Object> body, DocumentFormatting formatting) throws IOException {
        String html = buildHtml(body, formatting);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            registerFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new IOException("Ошибка генерации PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private void registerFonts(PdfRendererBuilder builder) {
        registerFontFamily(builder, FONT_FAMILY, EnumSet.of(FSFontUseCase.DOCUMENT));
        for (String alias : FONT_ALIASES) {
            registerFontFamily(builder, alias, EnumSet.of(FSFontUseCase.DOCUMENT));
        }
        // NotoSerif как fallback ДО base-14 (BUG-077): любое неразрешимое семейство
        // (в т.ч. font-family из textStyle-меток) получает шрифт с кириллицей,
        // а не Times-Roman/Helvetica/Courier без кириллических глифов.
        registerFontFamily(builder, FONT_FAMILY, EnumSet.of(FSFontUseCase.FALLBACK_PRE));
    }

    private void registerFontFamily(PdfRendererBuilder builder, String family, Set<FSFontUseCase> usedFor) {
        registerFont(builder, "/fonts/NotoSerif-Regular.ttf",    family, 400, PdfRendererBuilder.FontStyle.NORMAL, usedFor);
        registerFont(builder, "/fonts/NotoSerif-Bold.ttf",       family, 700, PdfRendererBuilder.FontStyle.NORMAL, usedFor);
        registerFont(builder, "/fonts/NotoSerif-Italic.ttf",     family, 400, PdfRendererBuilder.FontStyle.ITALIC, usedFor);
        registerFont(builder, "/fonts/NotoSerif-BoldItalic.ttf", family, 700, PdfRendererBuilder.FontStyle.ITALIC, usedFor);
    }

    private void registerFont(PdfRendererBuilder builder, String classpathPath, String family,
                              int weight, PdfRendererBuilder.FontStyle style, Set<FSFontUseCase> usedFor) {
        builder.useFont(() -> {
            InputStream is = getClass().getResourceAsStream(classpathPath);
            if (is == null) {
                log.warn("Font not found in classpath: {}", classpathPath);
                return null;
            }
            return is;
        }, family, weight, style, true, usedFor);
    }

    // ── HTML builder ─────────────────────────────────────────────────────────

    /**
     * Собирает HTML-представление тела документа. Package-private — используется
     * тестами для проверки рендера (BUG-066).
     */
    String buildHtml(Map<String, Object> body, DocumentFormatting f) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        sb.append(buildCss(f));
        sb.append("</style></head><body><div class=\"page\">");
        // Нумерация пунктов строится один раз на экспорт по экспортируемому дереву
        // (BUG-066) — состояние не хранится в полях сервиса (thread safety).
        renderNode(sb, body, ClauseNumbering.build(body));
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String buildCss(DocumentFormatting f) {
        if (f == null) return "body{font-family:\"" + FONT_FAMILY + "\";font-size:14pt;}";

        boolean landscape = "landscape".equals(f.getOrientation());
        int[] dims = pageDimensions(f.getPageSize(), landscape);
        PageMargins m = f.getMargins();
        int top = m != null ? m.getTop() : 20;
        int right = m != null ? m.getRight() : 15;
        int bottom = m != null ? m.getBottom() : 20;
        int left = m != null ? m.getLeft() : 30;

        String font = resolveFontStack(f.getDefaultFont());

        int fontSize = f.getDefaultFontSize() > 0 ? f.getDefaultFontSize() : 14;
        double lineH = f.getLineSpacing() > 0 ? f.getLineSpacing() : 1.5;

        // Locale.US обязателен: на ru_RU "%.2f" даёт "1,50", и openhtmltopdf
        // молча выбрасывает line-height (lineSpacing игнорировался).
        return String.format(java.util.Locale.US, """
                @page {
                  size: %dmm %dmm;
                  margin: %dmm %dmm %dmm %dmm;
                }
                body {
                  font-family: %s;
                  font-size: %dpt;
                  line-height: %.2f;
                  margin: 0;
                  padding: 0;
                }
                h1 { font-size: %.0fpt; margin: 12pt 0 6pt; }
                h2 { font-size: %.0fpt; margin: 10pt 0 5pt; }
                h3 { font-size: %.0fpt; margin: 8pt 0 4pt; }
                table { border-collapse: collapse; width: 100%%; margin: 6pt 0; border: none; }
                td, th { border: none; padding: 4pt 6pt; background-color: transparent; }
                th { font-weight: bold; }
                .param { color: #808080; }
                hr { border: none; border-top: 1px solid #aaa; margin: 8pt 0; }
                .page-break { page-break-before: always; }
                """,
                dims[0], dims[1],
                top, right, bottom, left,
                font, fontSize, lineH,
                fontSize * 1.71, fontSize * 1.43, fontSize * 1.21
        );
    }

    /**
     * Строит CSS-стек {@code font-family} так, чтобы кириллица гарантированно
     * резолвилась в приложенное NotoSerif (BUG-077): известные имена (generic
     * {@code serif}/{@code sans-serif}/{@code monospace}, base-14 Times/Helvetica/Courier
     * и их варианты написания) заменяются на {@code "Noto Serif"}, неизвестные
     * (например {@code Arial}) сохраняются как есть, а {@code "Noto Serif"} всегда
     * добавляется последним fallback-ом. {@code defaultFont} может быть как одним
     * именем, так и целым стеком ({@code "Times New Roman, serif"}).
     */
    private String resolveFontStack(String rawFont) {
        String quotedFamily = "\"" + FONT_FAMILY + "\"";
        List<String> families = new ArrayList<>();
        if (rawFont != null) {
            for (String part : rawFont.split(",")) {
                String name = part.replace("\"", "").trim();
                if (name.isEmpty()) continue;
                String family = BUNDLED_ALIASES.contains(name.toLowerCase(Locale.ROOT)) ? FONT_FAMILY : name;
                String quoted = "\"" + family + "\"";
                if (!families.contains(quoted)) families.add(quoted);
            }
        }
        if (!families.contains(quotedFamily)) families.add(quotedFamily);
        return String.join(", ", families);
    }

    private int[] pageDimensions(String pageSize, boolean landscape) {
        int w, h;
        switch (String.valueOf(pageSize)) {
            case "A3" -> { w = 297; h = 420; }
            case "Letter" -> { w = 216; h = 279; }
            default -> { w = 210; h = 297; }
        }
        return landscape ? new int[]{h, w} : new int[]{w, h};
    }

    // ── Node rendering ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void renderNode(StringBuilder sb, Map<String, Object> node, ClauseNumbering.Result numbering) {
        String type = String.valueOf(node.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");

        switch (type) {
            case "doc" -> {
                if (children != null) children.forEach(c -> renderNode(sb, c, numbering));
            }
            case "paragraph" -> renderParagraph(sb, node, null, numbering);
            case "heading" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                int level = attrs != null ? toInt(attrs.get("level"), 1) : 1;
                sb.append("<h").append(level).append(paragraphStyle(node)).append(">");
                if (children != null) children.forEach(c -> renderInline(sb, c, numbering));
                sb.append("</h").append(level).append(">");
            }
            case "bulletList" -> {
                sb.append("<ul>");
                if (children != null) children.forEach(c -> renderListItem(sb, c, numbering));
                sb.append("</ul>");
            }
            case "orderedList" -> {
                sb.append("<ol>");
                if (children != null) children.forEach(c -> renderListItem(sb, c, numbering));
                sb.append("</ol>");
            }
            case "numberedClause" -> renderNumberedClause(sb, node, children, numbering);
            case "table" -> renderTable(sb, children, numbering);
            case "horizontalRule" -> sb.append("<hr/>");
            case "pageBreak" -> sb.append("<div class=\"page-break\"></div>");
            case "conditionalBlock" -> {
                if (children != null) children.forEach(c -> renderNode(sb, c, numbering));
            }
            default -> {
                if (children != null) children.forEach(c -> renderNode(sb, c, numbering));
            }
        }
    }

    /** Абзац; {@code boldPrefix} — жирный префикс в начале (метка пункта, BUG-066). */
    @SuppressWarnings("unchecked")
    private void renderParagraph(
            StringBuilder sb, Map<String, Object> node, String boldPrefix, ClauseNumbering.Result numbering) {
        sb.append("<p").append(paragraphStyle(node)).append(">");
        if (boldPrefix != null) sb.append("<strong>").append(escape(boldPrefix)).append("</strong>");
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
        if (children != null) children.forEach(c -> renderInline(sb, c, numbering));
        else if (boldPrefix == null) sb.append("&#160;");
        sb.append("</p>");
    }

    /**
     * Пункт с автонумерацией (BUG-066): контент пункта — block-узлы
     * ({@code content: block+}), поэтому рендерится блочно через {@code renderNode},
     * а не как inline. Печатная метка ("1. ", "2.1. ") выводится в начале первого
     * абзаца пункта (или отдельным абзацем, если первый дочерний узел — не абзац);
     * метка берётся из {@link ClauseNumbering} — той же, что показывает
     * {@code AutoNumberingPlugin} в редакторе.
     */
    private void renderNumberedClause(
            StringBuilder sb,
            Map<String, Object> node,
            List<Map<String, Object>> children,
            ClauseNumbering.Result numbering) {
        List<Map<String, Object>> kids = children != null ? children : List.of();
        String label = numbering.labelFor(node);
        String labelRun = label != null ? label + " " : null;

        if (!kids.isEmpty() && "paragraph".equals(str(kids.get(0).get("type")))) {
            // метка — в начало первого абзаца пункта
            renderParagraph(sb, kids.get(0), labelRun, numbering);
            for (int i = 1; i < kids.size(); i++) renderNode(sb, kids.get(i), numbering);
        } else {
            if (labelRun != null) {
                sb.append("<p").append(paragraphStyle(node)).append("><strong>")
                        .append(escape(labelRun)).append("</strong></p>");
            }
            kids.forEach(c -> renderNode(sb, c, numbering));
        }
    }

    /**
     * Пункт списка (BUG-078): {@code listItem} — block-узлы ({@code content: block+}),
     * поэтому блочные дети (в т.ч. {@code numberedClause}, вложенные списки, таблицы)
     * рендерятся через {@code renderNode} — раньше рендерился только inline-контент
     * и текст блоков молча терялся. Маркировку списка ({@code <li>}) рисует движок;
     * инлайн-трюк (первый абзац без {@code <p>}, чтобы маркер остался на строке
     * пункта) применяется только если <b>первый дочерний узел</b> — абзац. Если
     * первый ребёнок не абзац (например, сразу {@code numberedClause}) — все дети
     * рендерятся блочно (в DOCX такой пункт печатается с маркером перед меткой
     * пункта, выводы согласованы).
     */
    @SuppressWarnings("unchecked")
    private void renderListItem(StringBuilder sb, Map<String, Object> item, ClauseNumbering.Result numbering) {
        sb.append("<li>");
        List<Map<String, Object>> children = (List<Map<String, Object>>) item.get("content");
        if (children != null && !children.isEmpty()
                && "paragraph".equals(str(children.get(0).get("type")))) {
            List<Map<String, Object>> inlines = (List<Map<String, Object>>) children.get(0).get("content");
            if (inlines != null) inlines.forEach(i -> renderInline(sb, i, numbering));
            for (int i = 1; i < children.size(); i++) renderNode(sb, children.get(i), numbering);
        } else if (children != null) {
            // прочие блочные дети — блочный рендер (BUG-078)
            children.forEach(c -> renderNode(sb, c, numbering));
        }
        sb.append("</li>");
    }

    @SuppressWarnings("unchecked")
    private void renderTable(StringBuilder sb, List<Map<String, Object>> rows, ClauseNumbering.Result numbering) {
        if (rows == null) return;
        sb.append("<table>");
        for (Map<String, Object> row : rows) {
            sb.append("<tr>");
            List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("content");
            if (cells != null) {
                for (Map<String, Object> cell : cells) {
                    boolean isHeader = "tableHeader".equals(cell.get("type"));
                    String tag = isHeader ? "th" : "td";
                    Map<String, Object> attrs = asMap(cell.get("attrs"));
                    String cellStyle = buildCellStyle(attrs);
                    sb.append("<").append(tag);
                    if (!cellStyle.isEmpty()) sb.append(" style=\"").append(cellStyle).append("\"");
                    sb.append(">");
                    List<Map<String, Object>> cellContent = (List<Map<String, Object>>) cell.get("content");
                    if (cellContent != null) {
                        for (Map<String, Object> block : cellContent) {
                            // BUG-078: ячейка таблицы — block-контент. Блочные дети идут
                            // через renderNode (иначе numberedClause и любой другой блок
                            // молча терял текст), inline-дети — прежним inline-путём.
                            String blockType = str(block.get("type"));
                            if (TipTapNodeTypes.isBlockType(blockType)) {
                                renderNode(sb, block, numbering);
                            } else {
                                List<Map<String, Object>> inlines =
                                        (List<Map<String, Object>>) block.get("content");
                                if (TipTapNodeTypes.isInlineType(blockType)) {
                                    renderInline(sb, block, numbering);
                                } else if (inlines != null) {
                                    inlines.forEach(i -> renderInline(sb, i, numbering));
                                }
                            }
                        }
                    }
                    sb.append("</").append(tag).append(">");
                }
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
    }

    @SuppressWarnings("unchecked")
    private String buildCellStyle(Map<String, Object> attrs) {
        if (attrs == null) return "";
        StringBuilder s = new StringBuilder();

        String va = str(attrs.get("verticalAlign"));
        if (va != null) s.append("vertical-align:").append(va).append(";");

        String bg = str(attrs.get("backgroundColor"));
        if (bg != null) s.append("background-color:").append(bg).append(";");

        String orient = str(attrs.get("textOrientation"));
        if ("rotate-90".equals(orient)) s.append("writing-mode:vertical-rl;");
        else if ("rotate-270".equals(orient)) s.append("writing-mode:vertical-lr;");
        else if ("rotate-180".equals(orient)) s.append("transform:rotate(180deg);");

        for (String side : new String[]{"borderTop", "borderRight", "borderBottom", "borderLeft"}) {
            String val = str(attrs.get(side));
            if (val != null) {
                String cssProp = side.replaceAll("([A-Z])", "-$1").toLowerCase();
                s.append(cssProp).append(":").append(val).append(";");
            }
        }

        Object mh = attrs.get("minHeight");
        if (mh != null) s.append("min-height:").append(toInt(mh, 0)).append("px;");

        return s.toString();
    }

    // ── Inline rendering ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void renderInline(StringBuilder sb, Map<String, Object> node, ClauseNumbering.Result numbering) {
        String type = String.valueOf(node.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
        switch (type) {
            case "text" -> {
                String text = escape(str(node.get("text")));
                List<Map<String, Object>> marks = (List<Map<String, Object>>) node.get("marks");
                if (marks != null && !marks.isEmpty()) {
                    sb.append(openMarks(marks)).append(text).append(closeMarks(marks));
                } else {
                    sb.append(text);
                }
            }
            case "hardBreak" -> sb.append("<br/>");
            case "parameterField" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                String display = attrs != null ? str(attrs.get("display")) : null;
                String code = attrs != null ? str(attrs.get("parameterCode")) : "?";
                sb.append("<span class=\"param\">{{").append(escape(display != null ? display : code)).append("}}</span>");
            }
            case "templateParam" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                String display = attrs != null ? str(attrs.get("display")) : null;
                String fieldId = attrs != null ? str(attrs.get("fieldId")) : "?";
                sb.append("<span class=\"param\">{{").append(escape(display != null ? display : fieldId)).append("}}</span>");
            }
            case "clauseRef" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                // BUG-078: в экспорте шаблона (без ответов) ссылка резолвится по
                // нумерации экспортируемого дерева: «п. <метка>» → attrs.label → «п. ?»
                sb.append(escape(refText(node, attrs, numbering)));
            }
            case "conditionalInline" -> {
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
                if (children != null) children.forEach(c -> renderInline(sb, c, numbering));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String openMarks(List<Map<String, Object>> marks) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> mark : marks) {
            String t = str(mark.get("type"));
            Map<String, Object> attrs = asMap(mark.get("attrs"));
            switch (String.valueOf(t)) { // null-безопасно: метка без type
                case "bold" -> sb.append("<strong>");
                case "italic" -> sb.append("<em>");
                case "underline" -> sb.append("<u>");
                case "strike" -> sb.append("<s>");
                case "subscript" -> sb.append("<sub>");
                case "superscript" -> sb.append("<sup>");
                case "link" -> {
                    String href = attrs != null ? str(attrs.get("href")) : "#";
                    sb.append("<a href=\"").append(escape(href)).append("\">");
                }
                case "textStyle" -> {
                    sb.append("<span style=\"");
                    if (attrs != null) {
                        if (attrs.get("color") != null) sb.append("color:").append(attrs.get("color")).append(";");
                        if (attrs.get("fontSize") != null) sb.append("font-size:").append(attrs.get("fontSize")).append(";");
                        if (attrs.get("fontFamily") != null) sb.append("font-family:").append(attrs.get("fontFamily")).append(";");
                    }
                    sb.append("\">");
                }
                case "highlight" -> {
                    String color = attrs != null ? str(attrs.get("color")) : "yellow";
                    sb.append("<span style=\"background:").append(color).append(";\">");
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String closeMarks(List<Map<String, Object>> marks) {
        StringBuilder sb = new StringBuilder();
        for (int i = marks.size() - 1; i >= 0; i--) {
            String t = String.valueOf(marks.get(i).get("type"));
            switch (String.valueOf(t)) { // null-безопасно: метка без type
                case "bold" -> sb.append("</strong>");
                case "italic" -> sb.append("</em>");
                case "underline" -> sb.append("</u>");
                case "strike" -> sb.append("</s>");
                case "subscript" -> sb.append("</sub>");
                case "superscript" -> sb.append("</sup>");
                case "link" -> sb.append("</a>");
                case "textStyle", "highlight" -> sb.append("</span>");
            }
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Текст {@code clauseRef} для экспорта шаблона (BUG-078): делегирует
     * {@link ClauseNumbering.Result#refText} — тот же порядок, что в DOCX
     * (метка → {@code attrs.label} как непустая строка → «п. ?»).
     */
    private String refText(Map<String, Object> refNode, Map<String, Object> attrs, ClauseNumbering.Result numbering) {
        if (numbering != null) return numbering.refText(refNode, attrs);
        String fallback = ClauseNumbering.refLabelFallback(attrs);
        return fallback != null ? fallback : "п. ?";
    }

    @SuppressWarnings("unchecked")
    private String paragraphStyle(Map<String, Object> node) {
        Map<String, Object> attrs = asMap(node.get("attrs"));
        if (attrs == null) return "";
        StringBuilder style = new StringBuilder(" style=\"");
        String align = str(attrs.get("textAlign"));
        if (align != null) style.append("text-align:").append(align).append(";");
        Object indent = attrs.get("indent");
        if (indent != null) style.append("margin-left:").append(toInt(indent, 0) * 2).append("em;");
        style.append("\"");
        return style.toString();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return def;
    }

    /** attrs/метка как Map; мусор (не-объект) трактуется как отсутствие attrs (BUG-063). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** Строковое значение атрибута; не-строки не роняют экспорт (BUG-063). */
    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }
}
