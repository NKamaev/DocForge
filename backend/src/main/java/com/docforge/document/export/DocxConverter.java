package com.docforge.document.export;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Converts TipTap JSON (as deserialized Map) to DOCX bytes.
 *
 * Supported nodes: doc, paragraph, heading, text (bold/italic/underline/strike/color/fontSize/fontFamily),
 * bulletList, orderedList, listItem, table, tableRow, tableHeader, tableCell, hardBreak.
 */
@Component
public class DocxConverter {

    // Default: 14pt = 28 half-points
    private static final int DEFAULT_SIZE_HALF = 28;
    private static final String DEFAULT_FONT   = "Times New Roman";

    // ── Public API ────────────────────────────────────────────────────────────

    public byte[] toDocx(Object body) throws IOException {
        XWPFDocument doc = new XWPFDocument();
        setupDefaultStyle(doc);

        Map<String, Object> root = asMap(body);
        List<Object> content = asList(root.get("content"));

        processNodes(doc, content, null, 0);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ── Document setup ────────────────────────────────────────────────────────

    private void setupDefaultStyle(XWPFDocument doc) {
        // Set page margins: A4, 20mm top/bottom, 30mm left, 15mm right
        CTSectPr sect = doc.getDocument().getBody().addNewSectPr();
        CTPageMar mar = sect.addNewPgMar();
        mar.setTop(BigInteger.valueOf(1134));    // 20mm in twentieths of a point
        mar.setBottom(BigInteger.valueOf(1134));
        mar.setLeft(BigInteger.valueOf(1701));   // 30mm
        mar.setRight(BigInteger.valueOf(851));   // 15mm
    }

    // ── Node dispatching ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void processNodes(XWPFDocument doc, List<Object> nodes,
                              BigInteger numId, int listLevel) {
        if (nodes == null) return;
        for (Object item : nodes) {
            Map<String, Object> node = asMap(item);
            processNode(doc, node, numId, listLevel);
        }
    }

    private void processNode(XWPFDocument doc, Map<String, Object> node,
                             BigInteger numId, int listLevel) {
        String type = str(node.get("type"));
        switch (type) {
            case "paragraph"   -> processParagraph(doc, node, numId, listLevel);
            case "heading"     -> processHeading(doc, node);
            case "bulletList"  -> processList(doc, node, false);
            case "orderedList" -> processList(doc, node, true);
            case "listItem"    -> processListItem(doc, node, numId, listLevel);
            case "table"       -> processTable(doc, node);
            // blockquote, codeBlock, horizontalRule — ignored for MVP
        }
    }

    // ── Paragraph ─────────────────────────────────────────────────────────────

    private void processParagraph(XWPFDocument doc, Map<String, Object> node,
                                  BigInteger numId, int listLevel) {
        XWPFParagraph p = doc.createParagraph();

        // Text alignment from attrs
        String align = strAttr(node, "textAlign");
        if (align != null) {
            p.setAlignment(toAlignment(align));
        }

        // If this paragraph is part of a list, attach numbering
        if (numId != null) {
            CTPPr pPr = p.getCTP().getPPr();
            if (pPr == null) pPr = p.getCTP().addNewPPr();
            CTNumPr numPr = pPr.addNewNumPr();
            numPr.addNewIlvl().setVal(BigInteger.valueOf(listLevel));
            numPr.addNewNumId().setVal(numId);
        }

        fillRuns(p, asList(node.get("content")));
    }

    // ── Heading ───────────────────────────────────────────────────────────────

    private void processHeading(XWPFDocument doc, Map<String, Object> node) {
        int level = intAttr(node, "level", 1);
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading" + level);
        fillRuns(p, asList(node.get("content")));
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    private void processList(XWPFDocument doc, Map<String, Object> node, boolean ordered) {
        BigInteger numId = addNumbering(doc, ordered);
        List<Object> items = asList(node.get("content"));
        if (items == null) return;
        for (Object item : items) {
            Map<String, Object> child = asMap(item);
            String type = str(child.get("type"));
            if ("listItem".equals(type)) {
                processListItem(doc, child, numId, 0);
            }
        }
    }

    private void processListItem(XWPFDocument doc, Map<String, Object> node,
                                 BigInteger numId, int level) {
        List<Object> children = asList(node.get("content"));
        if (children == null) return;
        for (Object child : children) {
            Map<String, Object> childNode = asMap(child);
            String type = str(childNode.get("type"));
            if ("paragraph".equals(type)) {
                processParagraph(doc, childNode, numId, level);
            } else {
                // nested list
                processNode(doc, childNode, numId, level + 1);
            }
        }
    }

    /** Registers a new abstract numbering + numbering instance and returns its numId. */
    private BigInteger addNumbering(XWPFDocument doc, boolean ordered) {
        XWPFNumbering numbering = doc.createNumbering();

        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(BigInteger.valueOf(
                (long) (Math.random() * 1_000_000)));

        // Define levels 0-8
        for (int i = 0; i < 9; i++) {
            CTLvl lvl = abstractNum.addNewLvl();
            lvl.setIlvl(BigInteger.valueOf(i));
            CTNumFmt fmt = lvl.addNewNumFmt();
            fmt.setVal(ordered ? STNumberFormat.DECIMAL : STNumberFormat.BULLET);
            CTLevelText txt = lvl.addNewLvlText();
            txt.setVal(ordered ? "%" + (i + 1) + "." : "•");
            CTInd ind = lvl.addNewPPr().addNewInd();
            ind.setLeft(BigInteger.valueOf(720L * (i + 1)));
            ind.setHanging(BigInteger.valueOf(360));
        }

        XWPFAbstractNum xwpfAbstract = new XWPFAbstractNum(abstractNum);
        BigInteger abstractNumId = numbering.addAbstractNum(xwpfAbstract);
        return numbering.addNum(abstractNumId);
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private void processTable(XWPFDocument doc, Map<String, Object> node) {
        List<Object> rowNodes = asList(node.get("content"));
        if (rowNodes == null || rowNodes.isEmpty()) return;

        // Count columns from first row
        Map<String, Object> firstRow = asMap(rowNodes.get(0));
        List<Object> firstCells = asList(firstRow.get("content"));
        int cols = firstCells != null ? firstCells.size() : 1;

        XWPFTable table = doc.createTable(rowNodes.size(), cols);
        table.setWidth("100%");

        for (int ri = 0; ri < rowNodes.size(); ri++) {
            Map<String, Object> rowNode = asMap(rowNodes.get(ri));
            List<Object> cellNodes = asList(rowNode.get("content"));
            XWPFTableRow row = table.getRow(ri);
            if (cellNodes == null) continue;

            for (int ci = 0; ci < cellNodes.size(); ci++) {
                Map<String, Object> cellNode = asMap(cellNodes.get(ci));
                XWPFTableCell cell = ci < row.getTableCells().size()
                        ? row.getCell(ci)
                        : row.createCell();

                // Clear default empty paragraph
                List<Object> cellContent = asList(cellNode.get("content"));
                if (cellContent != null) {
                    // First para already exists in cell
                    boolean first = true;
                    for (Object item : cellContent) {
                        Map<String, Object> pNode = asMap(item);
                        if ("paragraph".equals(str(pNode.get("type")))
                                || "heading".equals(str(pNode.get("type")))) {
                            XWPFParagraph p = first
                                    ? cell.getParagraphs().get(0)
                                    : cell.addParagraph();
                            first = false;
                            fillRuns(p, asList(pNode.get("content")));
                        }
                    }
                }
            }
        }
    }

    // ── Inline content (runs) ─────────────────────────────────────────────────

    private void fillRuns(XWPFParagraph p, List<Object> children) {
        if (children == null) return;
        for (Object item : children) {
            Map<String, Object> node = asMap(item);
            String type = str(node.get("type"));
            if ("text".equals(type)) {
                createRun(p, node);
            } else if ("hardBreak".equals(type)) {
                p.createRun().addCarriageReturn();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createRun(XWPFParagraph p, Map<String, Object> node) {
        String text = str(node.get("text"));
        XWPFRun run = p.createRun();
        run.setText(text != null ? text : "");

        // Defaults
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(DEFAULT_SIZE_HALF / 2);

        // Apply marks
        List<Map<String, Object>> marks = (List<Map<String, Object>>) node.get("marks");
        if (marks == null) return;

        for (Map<String, Object> mark : marks) {
            String markType = str(mark.get("type"));
            switch (markType) {
                case "bold"      -> run.setBold(true);
                case "italic"    -> run.setItalic(true);
                case "underline" -> run.setUnderline(UnderlinePatterns.SINGLE);
                case "strike"    -> run.setStrikeThrough(true);
                case "textStyle" -> applyTextStyle(run, asMap(mark.get("attrs")));
            }
        }
    }

    private void applyTextStyle(XWPFRun run, Map<String, Object> attrs) {
        if (attrs == null) return;

        String fontFamily = str(attrs.get("fontFamily"));
        if (fontFamily != null && !fontFamily.isBlank()) {
            run.setFontFamily(fontFamily);
        }

        // fontSize like "14pt" → int pts
        String fontSize = str(attrs.get("fontSize"));
        if (fontSize != null && fontSize.endsWith("pt")) {
            try {
                int pt = Integer.parseInt(fontSize.replace("pt", "").trim());
                run.setFontSize(pt);
            } catch (NumberFormatException ignored) { /* keep default */ }
        }

        // color like "#FF0000" → "FF0000"
        String color = str(attrs.get("color"));
        if (color != null && color.startsWith("#")) {
            run.setColor(color.substring(1).toUpperCase());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ParagraphAlignment toAlignment(String align) {
        return switch (align) {
            case "center"  -> ParagraphAlignment.CENTER;
            case "right"   -> ParagraphAlignment.RIGHT;
            case "justify" -> ParagraphAlignment.BOTH;
            default        -> ParagraphAlignment.LEFT;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object o) {
        return (List<Object>) o;
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private String strAttr(Map<String, Object> node, String key) {
        Object attrs = node.get("attrs");
        if (!(attrs instanceof Map<?, ?> m)) return null;
        Object val = m.get(key);
        return val != null ? val.toString() : null;
    }

    private int intAttr(Map<String, Object> node, String key, int def) {
        String val = strAttr(node, key);
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return def; }
    }
}
