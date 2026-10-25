package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.PageMargins;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DocxExportServiceImpl implements DocxExportService {

    // 1 mm = 56.6929 twips; OOXML uses twentieths of a point (twips)
    private static final double MM_TO_TWIPS = 56.6929;
    // 1 pt = 20 twips (half-point = 10 twips)
    private static final int PT_TO_HALF_PT = 2;

    @Override
    public byte[] export(Map<String, Object> body, DocumentFormatting formatting) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            applyPageSetup(doc, formatting);
            // Нумерация пунктов строится один раз на экспорт по экспортируемому дереву
            // (BUG-066) — состояние не хранится в полях сервиса (thread safety).
            RenderContext ctx = new RenderContext(formatting, ClauseNumbering.build(body));
            renderNode(doc, body, ctx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ── Page setup ────────────────────────────────────────────────────────────

    private void applyPageSetup(XWPFDocument doc, DocumentFormatting f) {
        if (f == null) return;

        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        // Page size
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        int[] dims = pageDimensions(f);
        pgSz.setW(BigInteger.valueOf(dims[0]));
        pgSz.setH(BigInteger.valueOf(dims[1]));
        if ("landscape".equals(f.getOrientation())) {
            pgSz.setOrient(STPageOrientation.LANDSCAPE);
        }

        // Margins
        PageMargins m = f.getMargins();
        if (m != null) {
            CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            pgMar.setTop(BigInteger.valueOf(mmToTwips(m.getTop())));
            pgMar.setBottom(BigInteger.valueOf(mmToTwips(m.getBottom())));
            pgMar.setLeft(BigInteger.valueOf(mmToTwips(m.getLeft())));
            pgMar.setRight(BigInteger.valueOf(mmToTwips(m.getRight())));
        }
    }

    /** Returns {width, height} in twips for the given formatting. */
    private int[] pageDimensions(DocumentFormatting f) {
        boolean landscape = "landscape".equals(f.getOrientation());
        int wMm, hMm;
        switch (String.valueOf(f.getPageSize())) {
            case "A3" -> { wMm = 297; hMm = 420; }
            case "Letter" -> { wMm = 216; hMm = 279; }
            default -> { wMm = 210; hMm = 297; } // A4
        }
        if (landscape) { int tmp = wMm; wMm = hMm; hMm = tmp; }
        return new int[]{(int) (wMm * MM_TO_TWIPS), (int) (hMm * MM_TO_TWIPS)};
    }

    private long mmToTwips(int mm) {
        return Math.round(mm * MM_TO_TWIPS);
    }

    // ── Node rendering ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void renderNode(IBody doc, Map<String, Object> node, RenderContext ctx) {
        String type = String.valueOf(node.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");

        switch (type) {
            case "doc" -> {
                if (children != null) children.forEach(c -> renderNode(doc, c, ctx));
            }
            case "paragraph" -> renderParagraph(doc, node, ctx);
            case "heading" -> renderHeading(doc, node, ctx);
            case "bulletList" -> renderList(doc, children, ctx, false);
            case "orderedList" -> renderList(doc, children, ctx, true);
            case "numberedClause" -> renderNumberedClause(doc, node, ctx, null, false);
            case "table" -> renderTable(doc, node, ctx);
            case "horizontalRule" -> renderHorizontalRule(doc, ctx);
            case "pageBreak" -> renderPageBreak(doc, ctx);
            case "conditionalBlock" -> {
                if (children != null) children.forEach(c -> renderNode(doc, c, ctx));
            }
            default -> {
                // unknown block — try to render children
                if (children != null) children.forEach(c -> renderNode(doc, c, ctx));
            }
        }
    }

    private void renderParagraph(IBody doc, Map<String, Object> node, RenderContext ctx) {
        renderParagraph(doc, node, ctx, null, null, false);
    }

    /**
     * Рендер абзаца. {@code marker} (если задан) — нежирный run в начале абзаца
     * (маркер списка, BUG-078); {@code boldPrefix} (если задан) — жирный run
     * (печатная метка пункта, BUG-066); {@code listIndent} — отступ пункта списка
     * (выставляется после атрибутов абзаца: пункт списка всегда сохраняет отступ).
     */
    @SuppressWarnings("unchecked")
    private void renderParagraph(IBody doc, Map<String, Object> node, RenderContext ctx,
                                 String marker, String boldPrefix, boolean listIndent) {
        XWPFParagraph para = newParagraph(doc);
        applyParagraphAttrs(para, node, ctx);
        if (listIndent) applyListIndent(para);
        if (marker != null) {
            para.createRun().setText(marker);
        }
        if (boldPrefix != null) {
            XWPFRun labelRun = para.createRun();
            labelRun.setBold(true);
            labelRun.setText(boldPrefix);
        }
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
        if (children != null) children.forEach(c -> renderInline(para, c, ctx));
    }

    @SuppressWarnings("unchecked")
    private void renderHeading(IBody doc, Map<String, Object> node, RenderContext ctx) {
        Map<String, Object> attrs = asMap(node.get("attrs"));
        int level = attrs != null ? toInt(attrs.get("level"), 1) : 1;

        XWPFParagraph para = newParagraph(doc);
        para.setStyle("Heading" + level);
        applyParagraphAttrs(para, node, ctx);

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
        if (children != null) children.forEach(c -> renderInline(para, c, ctx));
    }

    /**
     * Список. Каждый пункт списка — block-узлы ({@code content: block+}), поэтому
     * блочные дети (в т.ч. {@code numberedClause}, вложенные списки и таблицы)
     * рендерятся через {@code renderNode} (BUG-078): раньше рендерился только
     * {@code paragraph}-ребёнок, и текст прочих блоков терялся.
     *
     * <p>Маркер списка (буллит/номер) печатается по <b>первому дочернему узлу</b>
     * пункта (а не по первому {@code paragraph}-ребёнку: иначе маркер уезжал на
     * поздний абзац или в конец пункта):
     * <ul>
     *   <li>первый ребёнок — {@code paragraph}: маркер + отступ идут в этот абзац;</li>
     *   <li>первый ребёнок — {@code numberedClause}: маркер печатается перед
     *       печатной меткой пункта ({@code "• 2. "}) — одной строкой с текстом;</li>
     *   <li>иначе (вложенный список, таблица, пустой пункт): маркер — отдельным
     *       абзацем <b>перед</b> содержимым пункта.</li>
     * </ul>
     * Все абзацы пункта (в т.ч. внутри вложенных пунктов) сохраняют отступ списка.
     */
    @SuppressWarnings("unchecked")
    private void renderList(IBody doc, List<Map<String, Object>> items, RenderContext ctx, boolean ordered) {
        if (items == null) return;
        int seq = 1;
        for (Map<String, Object> item : items) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) item.get("content");
            if (children == null) children = List.of();
            // Простой список без нумерации через абстрактный список — используем отступ + префикс
            String prefix = ordered ? (seq++ + ". ") : "• ";

            int restFrom = 0;
            if (children.isEmpty()) {
                // пустой пункт — только маркер
                renderListMarker(doc, prefix);
            } else {
                Map<String, Object> first = children.get(0);
                String firstType = String.valueOf(first.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
                if ("paragraph".equals(firstType)) {
                    // маркер — в первый абзац пункта
                    renderParagraph(doc, first, ctx, prefix, null, true);
                    restFrom = 1;
                } else if ("numberedClause".equals(firstType)) {
                    // маркер — перед печатной меткой пункта ("• 2. ") одной строкой
                    renderNumberedClause(doc, first, ctx, prefix, true);
                    restFrom = 1;
                } else {
                    renderListMarker(doc, prefix);
                }
            }

            for (int i = restFrom; i < children.size(); i++) {
                renderItemBlock(doc, children.get(i), ctx, true);
            }
        }
    }

    /** Маркер списка отдельным абзацем с отступом пункта (пункт без абзацев). */
    private void renderListMarker(IBody doc, String prefix) {
        XWPFParagraph para = newParagraph(doc);
        applyListIndent(para);
        para.createRun().setText(prefix);
    }

    /**
     * Блок внутри пункта списка (или внутри пункта с печатной меткой, BUG-078):
     * абзацы и вложенные пункты сохраняют отступ пункта списка.
     */
    private void renderItemBlock(IBody doc, Map<String, Object> child, RenderContext ctx, boolean listItem) {
        if (listItem) {
            String type = String.valueOf(child.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
            if ("paragraph".equals(type)) {
                renderParagraph(doc, child, ctx, null, null, true);
                return;
            }
            if ("numberedClause".equals(type)) {
                renderNumberedClause(doc, child, ctx, null, true);
                return;
            }
        }
        renderNode(doc, child, ctx);
    }

    /** Отступ пункта списка (0.5 inch) — как в прежнем inline-рендере списка. */
    private void applyListIndent(XWPFParagraph para) {
        CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setLeft(BigInteger.valueOf(720)); // 0.5 inch
    }

    /**
     * Пункт с автонумерацией (BUG-066): контент пункта — block-узлы
     * ({@code content: block+}), поэтому рендерится блочно через {@code renderNode},
     * а не как inline. Печатная метка ("1. ", "2.1. ") выводится жирным run-ом
     * в начале первого абзаца пункта (или отдельным абзацем, если первый дочерний
     * узел — не абзац). Метка берётся из {@link ClauseNumbering} — той же, что
     * показывает {@code AutoNumberingPlugin} в редакторе.
     *
     * @param listMarker маркер списка ({@code "• "}, {@code "2. "}), если пункт —
     *                   первый блок пункта списка (BUG-078): печатается перед
     *                   меткой пункта одной строкой ({@code "• 2. "})
     * @param listItem   пункт внутри списка — абзацы сохраняют отступ списка
     */
    @SuppressWarnings("unchecked")
    private void renderNumberedClause(IBody doc, Map<String, Object> node, RenderContext ctx,
                                      String listMarker, boolean listItem) {
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
        if (children == null) children = List.of();

        String label = ctx.clauseLabel(node);
        String labelRun = label != null ? label + " " : null;

        if (!children.isEmpty() && "paragraph".equals(str(children.get(0).get("type")))) {
            // маркер списка + метка — в начало первого абзаца пункта
            renderParagraph(doc, children.get(0), ctx, listMarker, labelRun, listItem);
            for (int i = 1; i < children.size(); i++) {
                renderItemBlock(doc, children.get(i), ctx, listItem);
            }
        } else {
            if (listMarker != null || labelRun != null) {
                XWPFParagraph para = newParagraph(doc);
                applyParagraphAttrs(para, node, ctx);
                if (listItem) applyListIndent(para);
                if (listMarker != null) para.createRun().setText(listMarker);
                if (labelRun != null) {
                    XWPFRun run = para.createRun();
                    run.setBold(true);
                    run.setText(labelRun);
                }
            }
            for (Map<String, Object> child : children) {
                renderItemBlock(doc, child, ctx, listItem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void renderTable(IBody doc, Map<String, Object> node, RenderContext ctx) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) node.get("content");
        if (rows == null || rows.isEmpty()) return;

        XWPFTable table = newTable(doc);
        table.removeRow(0); // remove default empty row

        // Set transparent (no) borders by default — visible borders are applied per-cell via attrs
        CTTblPr tblPr = table.getCTTbl().getTblPr() != null
                ? table.getCTTbl().getTblPr()
                : table.getCTTbl().addNewTblPr();
        CTTblBorders tblBorders = tblPr.getTblBorders() != null
                ? tblPr.getTblBorders()
                : tblPr.addNewTblBorders();
        CTBorder[] borders = {
            tblBorders.getTop()    != null ? tblBorders.getTop()    : tblBorders.addNewTop(),
            tblBorders.getBottom() != null ? tblBorders.getBottom() : tblBorders.addNewBottom(),
            tblBorders.getLeft()   != null ? tblBorders.getLeft()   : tblBorders.addNewLeft(),
            tblBorders.getRight()  != null ? tblBorders.getRight()  : tblBorders.addNewRight(),
            tblBorders.getInsideH() != null ? tblBorders.getInsideH() : tblBorders.addNewInsideH(),
            tblBorders.getInsideV() != null ? tblBorders.getInsideV() : tblBorders.addNewInsideV()
        };
        for (CTBorder b : borders) {
            b.setVal(STBorder.NONE);
        }

        // Remove default table style (TableGrid adds black borders)
        if (tblPr.getTblStyle() != null) tblPr.unsetTblStyle();

        for (Map<String, Object> rowNode : rows) {
            List<Map<String, Object>> cells = (List<Map<String, Object>>) rowNode.get("content");
            if (cells == null) continue;

            XWPFTableRow row = table.createRow();
            // Remove default cell
            while (!row.getTableCells().isEmpty()) row.removeCell(0);

            // Collect minHeight values from cells to set row height
            int rowMinHeight = 0;

            for (Map<String, Object> cellNode : cells) {
                XWPFTableCell cell = row.createCell();
                Map<String, Object> cellAttrs = asMap(cellNode.get("attrs"));
                applyCellFormatting(cell, cellAttrs);

                if (cellAttrs != null) {
                    int mh = toInt(cellAttrs.get("minHeight"), 0);
                    if (mh > rowMinHeight) rowMinHeight = mh;
                }

                List<Map<String, Object>> cellContent = (List<Map<String, Object>>) cellNode.get("content");
                if (cellContent != null) {
                    for (Map<String, Object> blockNode : cellContent) {
                        // BUG-078: ячейка таблицы — block-контент. Блочные дети идут
                        // через renderNode (иначе numberedClause и любой другой блок
                        // молча терял текст), inline-дети — прежним inline-путём.
                        String blockType = str(blockNode.get("type"));
                        if (TipTapNodeTypes.isBlockType(blockType)) {
                            renderNode(cell, blockNode, ctx);
                        } else {
                            XWPFParagraph para = cell.getParagraphs().isEmpty()
                                    ? cell.getParagraphArray(0)
                                    : cell.addParagraph();
                            if (TipTapNodeTypes.isInlineType(blockType)) {
                                renderInline(para, blockNode, ctx);
                            } else {
                                renderInlineChildren(para, blockNode, ctx);
                            }
                        }
                    }
                }
            }

            // Apply minimum row height if any cell specified it
            if (rowMinHeight > 0) {
                row.setHeight(rowMinHeight * 15); // px * 15 ≈ twips
                row.setHeightRule(TableRowHeightRule.AT_LEAST);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCellFormatting(XWPFTableCell cell, Map<String, Object> attrs) {
        if (attrs == null) return;

        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();

        // Vertical alignment
        String va = str(attrs.get("verticalAlign"));
        if (va != null) {
            CTVerticalJc vAlign = tcPr.isSetVAlign() ? tcPr.getVAlign() : tcPr.addNewVAlign();
            vAlign.setVal(switch (va) {
                case "top" -> STVerticalJc.TOP;
                case "bottom" -> STVerticalJc.BOTTOM;
                default -> STVerticalJc.CENTER;
            });
        }

        // Background color — always set shading to avoid TableGrid default fill
        String bg = str(attrs.get("backgroundColor"));
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        if (bg != null && bg.startsWith("#")) {
            shd.setVal(STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill(bg.substring(1).toUpperCase());
        } else {
            shd.setVal(STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill("auto"); // transparent — no fill
        }

        // Text orientation
        String orient = str(attrs.get("textOrientation"));
        if (orient != null && !"horizontal".equals(orient)) {
            CTTextDirection td = tcPr.isSetTextDirection() ? tcPr.getTextDirection() : tcPr.addNewTextDirection();
            td.setVal(switch (orient) {
                case "rotate-90" -> STTextDirection.BT_LR;
                case "rotate-270" -> STTextDirection.TB_RL;
                default -> STTextDirection.LR_TB; // rotate-180 → normal (not supported in DOCX)
            });
        }

        // Cell borders
        boolean hasBorder = attrs.get("borderTop") != null || attrs.get("borderRight") != null
                || attrs.get("borderBottom") != null || attrs.get("borderLeft") != null;
        if (hasBorder) {
            CTTcBorders borders = tcPr.isSetTcBorders() ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
            applyCellBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop(), str(attrs.get("borderTop")));
            applyCellBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight(), str(attrs.get("borderRight")));
            applyCellBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(), str(attrs.get("borderBottom")));
            applyCellBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(), str(attrs.get("borderLeft")));
        }
    }

    /** Parse CSS border shorthand like "1px solid #000000" and apply to OOXML CTBorder. */
    private void applyCellBorder(CTBorder border, String css) {
        if (css == null || css.isBlank() || "none".equalsIgnoreCase(css)) {
            border.setVal(STBorder.NIL);
            return;
        }
        border.setVal(STBorder.SINGLE);
        // Extract width (px)
        java.util.regex.Matcher widthM = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)px").matcher(css);
        if (widthM.find()) {
            double px = Double.parseDouble(widthM.group(1));
            // 1px ≈ 10 eighths-of-a-pt (approx)
            border.setSz(BigInteger.valueOf(Math.round(px * 10)));
        } else {
            border.setSz(BigInteger.valueOf(8)); // default ~1px
        }
        // Extract color (#rrggbb)
        java.util.regex.Matcher colorM = java.util.regex.Pattern.compile("#([0-9a-fA-F]{6})").matcher(css);
        if (colorM.find()) {
            border.setColor(colorM.group(1).toUpperCase());
        } else {
            border.setColor("000000");
        }
        // Extract style
        if (css.contains("dashed")) {
            border.setVal(STBorder.DASHED);
        } else if (css.contains("dotted")) {
            border.setVal(STBorder.DOTTED);
        }
    }

    private void renderHorizontalRule(IBody doc, RenderContext ctx) {
        XWPFParagraph para = newParagraph(doc);
        CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTPBdr bdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder bottom = bdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(6));
        bottom.setSpace(BigInteger.valueOf(1));
        bottom.setColor("auto");
    }

    private void renderPageBreak(IBody doc, RenderContext ctx) {
        XWPFParagraph para = newParagraph(doc);
        XWPFRun run = para.createRun();
        run.addBreak(BreakType.PAGE);
    }

    // ── Inline rendering ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void renderInlineChildren(XWPFParagraph para, Map<String, Object> blockNode, RenderContext ctx) {
        List<Map<String, Object>> inlines = (List<Map<String, Object>>) blockNode.get("content");
        if (inlines != null) inlines.forEach(inline -> renderInline(para, inline, ctx));
    }

    @SuppressWarnings("unchecked")
    private void renderInline(XWPFParagraph para, Map<String, Object> node, RenderContext ctx) {
        String type = String.valueOf(node.get("type")); // BUG-063: не-строковый type не должен ронять экспорт
        switch (type) {
            case "text" -> {
                String text = str(node.get("text"));
                if (text == null) text = "";
                List<Map<String, Object>> marks = (List<Map<String, Object>>) node.get("marks");
                XWPFRun run = para.createRun();
                applyMarks(run, marks, ctx);
                // Handle newlines
                String[] lines = text.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) run.addCarriageReturn();
                    run.setText(lines[i], i == 0 ? 0 : run.getCTR().sizeOfTArray());
                }
            }
            case "hardBreak" -> {
                XWPFRun run = para.createRun();
                run.addCarriageReturn();
            }
            case "parameterField" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                String code = attrs != null ? str(attrs.get("parameterCode")) : "?";
                String display = attrs != null ? str(attrs.get("display")) : code;
                XWPFRun run = para.createRun();
                run.setText("{{" + (display != null ? display : code) + "}}");
                run.setColor("808080");
            }
            case "templateParam" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                String display = attrs != null ? str(attrs.get("display")) : null;
                String fieldId = attrs != null ? str(attrs.get("fieldId")) : "?";
                XWPFRun run = para.createRun();
                run.setText("{{" + (display != null ? display : fieldId) + "}}");
                run.setColor("808080");
            }
            case "clauseRef" -> {
                Map<String, Object> attrs = asMap(node.get("attrs"));
                // BUG-078: в экспорте шаблона (без ответов) ссылка резолвится по
                // нумерации экспортируемого дерева: «п. <метка>» → attrs.label → «п. ?»
                XWPFRun run = para.createRun();
                run.setText(ctx.clauseRefText(node, attrs));
                run.setColor("0563C1");
            }
            case "conditionalInline" -> {
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("content");
                if (children != null) children.forEach(c -> renderInline(para, c, ctx));
            }
            case "phrasingsRef" -> {
                // Для предварительного просмотра шаблона (без ответов) показываем имена формулировок
                @SuppressWarnings("unchecked")
                Map<String, Object> attrs = asMap(node.get("attrs"));
                String label = resolvePhrasingsDisplayLabel(attrs);
                XWPFRun run = para.createRun();
                run.setText("[" + label + "]");
                run.setColor("52C41A");
            }
        }
    }

    /** Возвращает метку для placeholder phrasingsRef в предпросмотре шаблона. */
    @SuppressWarnings("unchecked")
    private String resolvePhrasingsDisplayLabel(Map<String, Object> attrs) {
        if (attrs == null) return "?";
        // New format
        Object displayNamesObj = attrs.get("displayNames");
        if (displayNamesObj instanceof List<?> list && !list.isEmpty()) {
            // null-элементы отбрасываем: иначе NPE на Object::toString (BUG-063).
            String joined = list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!joined.isBlank()) {
                return joined;
            }
        }
        // Legacy fallback
        String displayName = str(attrs.get("displayName"));
        return displayName != null ? displayName : "?";
    }

    @SuppressWarnings("unchecked")
    private void applyMarks(XWPFRun run, List<Map<String, Object>> marks, RenderContext ctx) {
        if (marks == null) return;

        // Apply defaults from formatting
        if (ctx.formatting != null) {
            if (ctx.formatting.getDefaultFont() != null) {
                String font = ctx.formatting.getDefaultFont().split(",")[0].trim();
                run.setFontFamily(font);
            }
            if (ctx.formatting.getDefaultFontSize() > 0) {
                run.setFontSize(ctx.formatting.getDefaultFontSize());
            }
        }

        for (Map<String, Object> mark : marks) {
            String markType = str(mark.get("type"));
            Map<String, Object> attrs = asMap(mark.get("attrs"));

            switch (String.valueOf(markType)) { // null-безопасно: метка без type
                case "bold" -> run.setBold(true);
                case "italic" -> run.setItalic(true);
                case "underline" -> run.setUnderline(UnderlinePatterns.SINGLE);
                case "strike" -> run.setStrikeThrough(true);
                case "subscript" -> run.setSubscript(VerticalAlign.SUBSCRIPT);
                case "superscript" -> run.setSubscript(VerticalAlign.SUPERSCRIPT);
                case "textStyle" -> {
                    if (attrs != null) {
                        String color = str(attrs.get("color"));
                        if (color != null && color.startsWith("#")) run.setColor(color.substring(1));
                        Object fontSize = attrs.get("fontSize");
                        if (fontSize != null) {
                            String sizeStr = fontSize.toString().replace("pt", "").trim();
                            try { run.setFontSize(Double.parseDouble(sizeStr)); } catch (NumberFormatException ignored) {}
                        }
                        String fontFamily = str(attrs.get("fontFamily"));
                        if (fontFamily != null && !fontFamily.isBlank()) {
                            run.setFontFamily(fontFamily.split(",")[0].trim());
                        }
                    }
                }
                case "link" -> {
                    // Represent links as underlined text; real hyperlinks require relationship
                    run.setUnderline(UnderlinePatterns.SINGLE);
                    run.setColor("0563C1");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyParagraphAttrs(XWPFParagraph para, Map<String, Object> node, RenderContext ctx) {
        Map<String, Object> attrs = asMap(node.get("attrs"));
        if (attrs == null) return;

        String textAlign = str(attrs.get("textAlign"));
        if (textAlign != null) {
            para.setAlignment(switch (textAlign) {
                case "center" -> ParagraphAlignment.CENTER;
                case "right" -> ParagraphAlignment.RIGHT;
                case "justify" -> ParagraphAlignment.BOTH;
                default -> ParagraphAlignment.LEFT;
            });
        }

        Object indent = attrs.get("indent");
        if (indent != null) {
            int indentEm = toInt(indent, 0);
            if (indentEm > 0) {
                CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
                CTInd ctInd = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
                ctInd.setLeft(BigInteger.valueOf(indentEm * 360L)); // ~2em in twips
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Новый абзац в контейнере блоков — документ или ячейка таблицы (BUG-078):
     * POI 5.3 {@code IBody} не имеет {@code createParagraph}, у {@code XWPFTableCell}
     * абзац добавляется через {@code addParagraph}.
     */
    private XWPFParagraph newParagraph(IBody doc) {
        if (doc instanceof XWPFTableCell cell) return cell.addParagraph();
        return ((XWPFDocument) doc).createParagraph();
    }

    /** Новая таблица в контейнере блоков — документ или ячейка таблицы (BUG-078). */
    private XWPFTable newTable(IBody doc) {
        if (doc instanceof XWPFTableCell cell) {
            XWPFTable table = new XWPFTable(cell.getCTTc().addNewTbl(), cell);
            cell.insertTable(cell.getBodyElements().size(), table);
            return table;
        }
        return ((XWPFDocument) doc).createTable();
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {} }
        return def;
    }

    /**
     * Контекст рендера одного экспорта: форматирование страницы + нумерация пунктов
     * (BUG-066). Нумерация строится на каждый вызов {@code export} — состояние
     * не разделяется между потоками.
     */
    private record RenderContext(DocumentFormatting formatting, ClauseNumbering.Result numbering) {

        /** Печатная метка узла пункта ("1.", "2.1.") или {@code null}. */
        String clauseLabel(Map<String, Object> clauseNode) {
            return numbering != null ? numbering.labelFor(clauseNode) : null;
        }

        /**
         * Текст {@code clauseRef} для экспорта шаблона (BUG-078):
         * «п. <метка>» по {@link ClauseNumbering} (refId → метка с завершающей точкой),
         * затем {@code attrs.label} (только непустая строка), затем «п. ?» — в DOCX и PDF одинаково.
         */
        String clauseRefText(Map<String, Object> refNode, Map<String, Object> attrs) {
            if (numbering != null) return numbering.refText(refNode, attrs);
            String fallback = ClauseNumbering.refLabelFallback(attrs);
            return fallback != null ? fallback : "п. ?";
        }
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
