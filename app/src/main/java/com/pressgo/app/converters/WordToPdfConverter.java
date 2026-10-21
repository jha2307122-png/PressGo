package com.pressgo.app.converters;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.pressgo.app.utils.DocumentUtils;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Production-grade Word to PDF Converter for PressGo.
 * 100% Pure Java implementation supporting DOCX (OOXML) and DOC/RTF.
 * Extracts paragraphs, headings, typography, tables, and embedded images,
 * rendering them into print-ready A4 PDF documents.
 */
public final class WordToPdfConverter {

    private static final String TAG = "WordToPdfConverter";

    private WordToPdfConverter() {}

    /**
     * Converts a Word document (DOCX or DOC) from a URI into a PDF file.
     *
     * @param context    Android Context
     * @param uri        Uri of the source Word document
     * @param outputFile Target PDF file
     * @return Converted PDF File
     * @throws Exception On conversion failure
     */
    public static File convertWordToPdf(Context context, Uri uri, File outputFile) throws Exception {
        if (context == null || uri == null || outputFile == null) {
            throw new IllegalArgumentException("Context, Uri, and OutputFile must not be null");
        }

        byte[] inputBytes = readAllBytes(context, uri);
        if (inputBytes == null || inputBytes.length == 0) {
            throw new Exception("Selected Word file is empty or could not be read");
        }

        convertWordBytesToPdf(inputBytes, DocumentUtils.getFileName(context, uri), outputFile);
        return outputFile;
    }

    /**
     * Converts raw bytes of a Word document into a PDF file.
     */
    public static void convertWordBytesToPdf(byte[] inputBytes, String originalFileName, File outputFile) throws Exception {
        if (isZipArchive(inputBytes)) {
            convertDocx(inputBytes, originalFileName, outputFile);
        } else {
            convertLegacyOrRtfDoc(inputBytes, originalFileName, outputFile);
        }
    }

    /**
     * Estimates page count of a Word document (DOCX or DOC) for price calculation.
     */
    public static int estimateWordPageCount(Context context, Uri uri) {
        try {
            byte[] bytes = readAllBytes(context, uri);
            if (bytes == null || bytes.length == 0) return 1;

            if (isZipArchive(bytes)) {
                // Read word/document.xml and count paragraphs & tables
                byte[] docXml = extractZipEntry(bytes, "word/document.xml");
                if (docXml != null) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    org.w3c.dom.Document doc = db.parse(new ByteArrayInputStream(docXml));

                    NodeList pNodes = doc.getElementsByTagName("w:p");
                    if (pNodes.getLength() == 0) pNodes = doc.getElementsByTagName("p");
                    int pCount = pNodes.getLength();

                    NodeList tblNodes = doc.getElementsByTagName("w:tbl");
                    if (tblNodes.getLength() == 0) tblNodes = doc.getElementsByTagName("tbl");
                    int tblCount = tblNodes.getLength();

                    // Estimate ~20-25 paragraphs per page or ~2 tables per page
                    int estimated = Math.max(1, (int) Math.ceil((pCount + tblCount * 8.0) / 22.0));
                    return estimated;
                }
            } else {
                // Rough estimate based on file size and text length
                int length = bytes.length;
                return Math.max(1, (int) Math.ceil(length / 3500.0));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error estimating Word page count", e);
        }
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCX Conversion Engine
    // ─────────────────────────────────────────────────────────────────────────

    private static void convertDocx(byte[] docxBytes, String originalFileName, File outputFile) throws Exception {
        Map<String, byte[]> entries = extractAllZipEntries(docxBytes);

        byte[] documentXmlBytes = entries.get("word/document.xml");
        if (documentXmlBytes == null) {
            throw new Exception("Invalid DOCX format: word/document.xml was not found in archive");
        }

        // Parse relationships to map rId to image files
        Map<String, String> relsMap = new HashMap<>();
        byte[] relsBytes = entries.get("word/_rels/document.xml.rels");
        if (relsBytes != null) {
            parseRelationships(relsBytes, relsMap);
        }

        // Setup iText 7 document
        PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(42f, 42f, 42f, 42f);

        float printableWidth = PageSize.A4.getWidth() - document.getLeftMargin() - document.getRightMargin();

        // Optional document title banner if known
        if (originalFileName != null && !originalFileName.isEmpty()) {
            Paragraph titleHeader = new Paragraph(DocumentUtils.getFileNameWithoutExtension(originalFileName))
                    .setFontSize(8.5f)
                    .setFontColor(new DeviceRgb(148, 163, 184))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginBottom(16f);
            document.add(titleHeader);
        }

        // Parse word/document.xml
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document xmlDoc = db.parse(new ByteArrayInputStream(documentXmlBytes));

        Element body = findBodyElement(xmlDoc);
        if (body == null) {
            document.add(new Paragraph("(Empty document body)"));
            document.close();
            return;
        }

        NodeList children = body.getChildNodes();
        int elementsAdded = 0;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            if (isTag(child, "p")) {
                Element pElem = (Element) child;
                boolean added = processParagraph(pElem, document, relsMap, entries, printableWidth);
                if (added) elementsAdded++;
            } else if (isTag(child, "tbl")) {
                Element tblElem = (Element) child;
                Table table = processTable(tblElem, relsMap, entries, printableWidth);
                if (table != null) {
                    document.add(table);
                    elementsAdded++;
                }
            } else if (isTag(child, "sdt")) {
                // Structured Document Tag - process internal paragraphs and tables
                Element sdtContent = findFirstDescendant(child, "sdtContent");
                if (sdtContent != null) {
                    NodeList sdtChildren = sdtContent.getChildNodes();
                    for (int j = 0; j < sdtChildren.getLength(); j++) {
                        Node sdtChild = sdtChildren.item(j);
                        if (isTag(sdtChild, "p")) {
                            if (processParagraph((Element) sdtChild, document, relsMap, entries, printableWidth)) {
                                elementsAdded++;
                            }
                        } else if (isTag(sdtChild, "tbl")) {
                            Table table = processTable((Element) sdtChild, relsMap, entries, printableWidth);
                            if (table != null) {
                                document.add(table);
                                elementsAdded++;
                            }
                        }
                    }
                }
            }
        }

        if (elementsAdded == 0) {
            document.add(new Paragraph("(Word document contains no printable text or elements)")
                    .setFontColor(new DeviceRgb(100, 116, 139)));
        }

        document.close();
    }

    private static boolean processParagraph(Element pElem, Document document,
                                            Map<String, String> relsMap,
                                            Map<String, byte[]> entries,
                                            float printableWidth) {
        Paragraph p = new Paragraph();

        // Check paragraph properties
        Element pPr = findFirstChild(pElem, "pPr");
        boolean isHeading1 = false;
        boolean isHeading2 = false;
        boolean isTitle = false;

        if (pPr != null) {
            // Check style (Heading 1, Heading 2, Title, etc.)
            Element pStyle = findFirstChild(pPr, "pStyle");
            if (pStyle != null) {
                String val = getAttr(pStyle, "val");
                if (val != null) {
                    String v = val.toLowerCase(Locale.US);
                    if (v.contains("heading1") || v.equals("1") || v.contains("title")) {
                        isHeading1 = true;
                    } else if (v.contains("heading2") || v.equals("2") || v.contains("subtitle")) {
                        isHeading2 = true;
                    }
                }
            }

            // Check alignment (left, center, right, both)
            Element jc = findFirstChild(pPr, "jc");
            if (jc != null) {
                String val = getAttr(jc, "val");
                if ("center".equalsIgnoreCase(val)) {
                    p.setTextAlignment(TextAlignment.CENTER);
                } else if ("right".equalsIgnoreCase(val)) {
                    p.setTextAlignment(TextAlignment.RIGHT);
                } else if ("both".equalsIgnoreCase(val) || "justify".equalsIgnoreCase(val)) {
                    p.setTextAlignment(TextAlignment.JUSTIFIED);
                } else {
                    p.setTextAlignment(TextAlignment.LEFT);
                }
            }

            // Check bullet list / numbering
            Element numPr = findFirstChild(pPr, "numPr");
            if (numPr != null) {
                p.add(new Text("•  ").setBold().setFontColor(new DeviceRgb(30, 136, 229)));
                p.setMarginLeft(16f);
            }
        }

        if (isHeading1) {
            p.setFontSize(18f).setBold().setFontColor(new DeviceRgb(30, 58, 138)).setMarginTop(14f).setMarginBottom(6f);
        } else if (isHeading2) {
            p.setFontSize(14f).setBold().setFontColor(new DeviceRgb(37, 99, 235)).setMarginTop(10f).setMarginBottom(4f);
        } else {
            p.setFontSize(10.5f).setFontColor(new DeviceRgb(30, 41, 59)).setMarginBottom(4f);
        }

        boolean hasContent = false;
        NodeList childNodes = pElem.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node n = childNodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;

            if (isTag(n, "r")) { // Run
                Element rElem = (Element) n;
                Text text = processRun(rElem);
                if (text != null && !text.getText().isEmpty()) {
                    p.add(text);
                    hasContent = true;
                }

                // Check for inline images inside run
                Image img = extractDrawingImage(rElem, relsMap, entries, printableWidth);
                if (img != null) {
                    if (hasContent) {
                        document.add(p);
                        p = new Paragraph().setFontSize(10.5f).setMarginBottom(4f);
                        hasContent = false;
                    }
                    document.add(img);
                }

                // Check for page break in run
                Element br = findFirstChild(rElem, "br");
                if (br != null) {
                    String type = getAttr(br, "type");
                    if ("page".equalsIgnoreCase(type)) {
                        if (hasContent) {
                            document.add(p);
                            p = new Paragraph().setFontSize(10.5f).setMarginBottom(4f);
                            hasContent = false;
                        }
                        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                    } else {
                        p.add(new Text("\n"));
                    }
                }

                // Check for tab
                Element tab = findFirstChild(rElem, "tab");
                if (tab != null) {
                    p.add(new Text("    "));
                    hasContent = true;
                }

            } else if (isTag(n, "hyperlink")) {
                // Hyperlink containing runs
                NodeList hlChildren = n.getChildNodes();
                for (int h = 0; h < hlChildren.getLength(); h++) {
                    Node hlNode = hlChildren.item(h);
                    if (isTag(hlNode, "r")) {
                        Text hlText = processRun((Element) hlNode);
                        if (hlText != null && !hlText.getText().isEmpty()) {
                            hlText.setUnderline().setFontColor(new DeviceRgb(37, 99, 235));
                            p.add(hlText);
                            hasContent = true;
                        }
                    }
                }
            } else if (isTag(n, "drawing") || isTag(n, "pict")) {
                Image img = extractDrawingImage((Element) n, relsMap, entries, printableWidth);
                if (img != null) {
                    if (hasContent) {
                        document.add(p);
                        p = new Paragraph().setFontSize(10.5f).setMarginBottom(4f);
                        hasContent = false;
                    }
                    document.add(img);
                }
            }
        }

        if (hasContent) {
            document.add(p);
            return true;
        } else {
            // Empty paragraph adds slight vertical whitespace
            document.add(new Paragraph(" ").setFontSize(6f).setMargin(0));
            return false;
        }
    }

    private static Text processRun(Element rElem) {
        // Collect text within run
        StringBuilder sb = new StringBuilder();
        NodeList texts = rElem.getChildNodes();
        for (int i = 0; i < texts.getLength(); i++) {
            Node tNode = texts.item(i);
            if (isTag(tNode, "t")) {
                sb.append(tNode.getTextContent());
            }
        }

        if (sb.length() == 0) return null;

        Text text = new Text(sb.toString());

        // Process run properties
        Element rPr = findFirstChild(rElem, "rPr");
        if (rPr != null) {
            // Bold
            Element b = findFirstChild(rPr, "b");
            if (b != null) {
                String val = getAttr(b, "val");
                if (val == null || "true".equalsIgnoreCase(val) || "1".equals(val)) {
                    text.setBold();
                }
            }

            // Italic
            Element it = findFirstChild(rPr, "i");
            if (it != null) {
                String val = getAttr(it, "val");
                if (val == null || "true".equalsIgnoreCase(val) || "1".equals(val)) {
                    text.setItalic();
                }
            }

            // Underline
            Element u = findFirstChild(rPr, "u");
            if (u != null) {
                String val = getAttr(u, "val");
                if (val == null || !"none".equalsIgnoreCase(val)) {
                    text.setUnderline();
                }
            }

            // Strikethrough
            Element strike = findFirstChild(rPr, "strike");
            if (strike != null) {
                text.setLineThrough();
            }

            // Font size (sz in half-points: 24 = 12pt)
            Element sz = findFirstChild(rPr, "sz");
            if (sz != null) {
                String val = getAttr(sz, "val");
                if (val != null) {
                    try {
                        float pt = Float.parseFloat(val) / 2.0f;
                        if (pt >= 5f && pt <= 72f) {
                            text.setFontSize(pt);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Color
            Element color = findFirstChild(rPr, "color");
            if (color != null) {
                String val = getAttr(color, "val");
                DeviceRgb rgb = parseHexColor(val);
                if (rgb != null) {
                    text.setFontColor(rgb);
                }
            }

            // Highlight
            Element highlight = findFirstChild(rPr, "highlight");
            if (highlight != null) {
                String val = getAttr(highlight, "val");
                if ("yellow".equalsIgnoreCase(val)) {
                    text.setBackgroundColor(new DeviceRgb(254, 240, 138));
                } else if ("cyan".equalsIgnoreCase(val)) {
                    text.setBackgroundColor(new DeviceRgb(186, 230, 253));
                }
            }
        }

        return text;
    }

    private static Table processTable(Element tblElem,
                                      Map<String, String> relsMap,
                                      Map<String, byte[]> entries,
                                      float printableWidth) {
        try {
            // Count maximum columns in table
            int maxCols = 1;
            NodeList trList = tblElem.getElementsByTagName("w:tr");
            if (trList.getLength() == 0) trList = tblElem.getElementsByTagName("tr");
            if (trList.getLength() == 0) return null;

            for (int r = 0; r < trList.getLength(); r++) {
                Node tr = trList.item(r);
                if (tr.getNodeType() != Node.ELEMENT_NODE) continue;
                NodeList tcList = ((Element) tr).getElementsByTagName("w:tc");
                if (tcList.getLength() == 0) tcList = ((Element) tr).getElementsByTagName("tc");
                if (tcList.getLength() > maxCols) {
                    maxCols = tcList.getLength();
                }
            }

            float[] colWidths = new float[maxCols];
            for (int c = 0; c < maxCols; c++) {
                colWidths[c] = 1f;
            }

            Table table = new Table(UnitValue.createPercentArray(colWidths));
            table.setWidth(UnitValue.createPercentValue(100));
            table.setMarginTop(8f);
            table.setMarginBottom(10f);

            for (int r = 0; r < trList.getLength(); r++) {
                Node tr = trList.item(r);
                if (tr.getNodeType() != Node.ELEMENT_NODE) continue;

                NodeList tcList = ((Element) tr).getElementsByTagName("w:tc");
                if (tcList.getLength() == 0) tcList = ((Element) tr).getElementsByTagName("tc");

                for (int c = 0; c < tcList.getLength(); c++) {
                    Element tc = (Element) tcList.item(c);

                    int colSpan = 1;
                    Element tcPr = findFirstChild(tc, "tcPr");
                    if (tcPr != null) {
                        Element gridSpan = findFirstChild(tcPr, "gridSpan");
                        if (gridSpan != null) {
                            String val = getAttr(gridSpan, "val");
                            if (val != null) {
                                try {
                                    int span = Integer.parseInt(val);
                                    if (span > 1) colSpan = span;
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    Cell cell = colSpan > 1 ? new Cell(1, colSpan) : new Cell();
                    cell.setPadding(6f);
                    cell.setBorder(new SolidBorder(new DeviceRgb(203, 213, 225), 0.75f));

                    // First row header styling
                    if (r == 0) {
                        cell.setBackgroundColor(new DeviceRgb(241, 245, 249));
                    }

                    // Check custom cell shading
                    if (tcPr != null) {
                        Element shd = findFirstChild(tcPr, "shd");
                        if (shd != null) {
                            String fill = getAttr(shd, "fill");
                            DeviceRgb rgb = parseHexColor(fill);
                            if (rgb != null) cell.setBackgroundColor(rgb);
                        }
                    }

                    // Cell content paragraphs
                    NodeList pNodes = tc.getElementsByTagName("w:p");
                    if (pNodes.getLength() == 0) pNodes = tc.getElementsByTagName("p");
                    boolean cellHasContent = false;

                    for (int pIdx = 0; pIdx < pNodes.getLength(); pIdx++) {
                        Element pElem = (Element) pNodes.item(pIdx);
                        String text = pElem.getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            Paragraph cellP = new Paragraph(text.trim())
                                    .setFontSize(9.5f)
                                    .setMargin(0);
                            if (r == 0) cellP.setBold();
                            cell.add(cellP);
                            cellHasContent = true;
                        }

                        // Also check if cell paragraph contains an image
                        Image cellImg = extractDrawingImage(pElem, relsMap, entries, printableWidth / maxCols);
                        if (cellImg != null) {
                            cell.add(cellImg);
                            cellHasContent = true;
                        }
                    }

                    if (!cellHasContent) {
                        cell.add(new Paragraph(" ").setFontSize(8f).setMargin(0));
                    }

                    table.addCell(cell);
                }
            }

            return table;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Word table", e);
            return null;
        }
    }

    private static Image extractDrawingImage(Element element,
                                             Map<String, String> relsMap,
                                             Map<String, byte[]> entries,
                                             float maxWidth) {
        try {
            // Find blip r:embed or imagedata r:id
            String rId = null;
            NodeList blipList = element.getElementsByTagName("a:blip");
            if (blipList.getLength() == 0) blipList = element.getElementsByTagName("blip");
            if (blipList.getLength() > 0) {
                Element blip = (Element) blipList.item(0);
                rId = getAttr(blip, "embed");
                if (rId == null) rId = getAttr(blip, "r:embed");
            }

            if (rId == null) {
                NodeList vImg = element.getElementsByTagName("v:imagedata");
                if (vImg.getLength() == 0) vImg = element.getElementsByTagName("imagedata");
                if (vImg.getLength() > 0) {
                    Element vElem = (Element) vImg.item(0);
                    rId = getAttr(vElem, "id");
                    if (rId == null) rId = getAttr(vElem, "r:id");
                }
            }

            if (rId == null || !relsMap.containsKey(rId)) return null;

            String target = relsMap.get(rId);
            if (target == null) return null;

            byte[] imgBytes = findZipEntry(entries, target);

            if (imgBytes != null && imgBytes.length > 0) {
                ImageData imgData = ImageDataFactory.create(imgBytes);
                Image img = new Image(imgData);
                img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                img.setMarginTop(8f);
                img.setMarginBottom(8f);

                float imgW = img.getImageWidth();
                float imgH = img.getImageHeight();
                float maxHeight = PageSize.A4.getHeight() - 120f;

                float scaleW = imgW > maxWidth ? (maxWidth / imgW) : 1.0f;
                float scaleH = imgH > maxHeight ? (maxHeight / imgH) : 1.0f;
                float scale = Math.min(scaleW, scaleH);

                if (scale < 1.0f) {
                    img.scaleToFit(imgW * scale, imgH * scale);
                }
                return img;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting drawing image from DOCX", e);
        }
        return null;
    }

    private static byte[] findZipEntry(Map<String, byte[]> entries, String target) {
        if (target == null || entries == null) return null;
        byte[] b = entries.get(target);
        if (b != null) return b;

        String clean = target.replace("../", "").replaceAll("^/+", "");
        b = entries.get(clean);
        if (b != null) return b;

        String wordKey = clean.startsWith("word/") ? clean : ("word/" + clean);
        b = entries.get(wordKey);
        if (b != null) return b;

        // Case-insensitive fallback
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().equalsIgnoreCase(target) || e.getKey().equalsIgnoreCase(clean) || e.getKey().equalsIgnoreCase(wordKey)) {
                return e.getValue();
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy / RTF DOC Fallback
    // ─────────────────────────────────────────────────────────────────────────

    private static void convertLegacyOrRtfDoc(byte[] inputBytes, String originalFileName, File outputFile) throws Exception {
        PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(42f, 42f, 42f, 42f);

        if (originalFileName != null && !originalFileName.isEmpty()) {
            Paragraph titleHeader = new Paragraph(DocumentUtils.getFileNameWithoutExtension(originalFileName))
                    .setFontSize(8.5f)
                    .setFontColor(new DeviceRgb(148, 163, 184))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginBottom(16f);
            document.add(titleHeader);
        }

        List<String> paragraphs = extractTextRunsFromBinaryOrRtf(inputBytes);

        if (paragraphs.isEmpty()) {
            document.add(new Paragraph("(No readable text content extracted from legacy Word document)")
                    .setFontColor(new DeviceRgb(100, 116, 139)));
        } else {
            for (String pText : paragraphs) {
                String clean = pText.trim();
                if (clean.isEmpty()) {
                    document.add(new Paragraph(" ").setFontSize(6f).setMargin(0));
                } else {
                    document.add(new Paragraph(clean)
                            .setFontSize(10.5f)
                            .setFontColor(new DeviceRgb(30, 41, 59))
                            .setMarginBottom(4f));
                }
            }
        }

        document.close();
    }

    private static List<String> extractTextRunsFromBinaryOrRtf(byte[] bytes) {
        List<String> result = new ArrayList<>();
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);

        // Check if RTF
        if (raw.startsWith("{\\rtf")) {
            // Strip RTF control words
            String clean = raw.replaceAll("\\\\[a-zA-Z0-9]+ ?", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("\\par", "\n");
            for (String line : clean.split("\r?\n")) {
                if (!line.trim().isEmpty()) result.add(line.trim());
            }
            return result;
        }

        // Binary .doc: Scan for valid ASCII/UTF-8 words and paragraphs
        StringBuilder current = new StringBuilder();
        int consecutiveChars = 0;

        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '\n' || b == '\r') {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
            } else if (b >= 32 && b <= 126) {
                current.append((char) b);
                consecutiveChars++;
            } else if (b == 9) { // Tab
                current.append("    ");
            } else {
                if (consecutiveChars < 3 && current.length() > 0) {
                    current.setLength(0);
                }
                consecutiveChars = 0;
            }
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper Methods & XML Traversal
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isZipArchive(byte[] bytes) {
        return bytes != null && bytes.length >= 4 &&
                bytes[0] == 0x50 && bytes[1] == 0x4B &&
                (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07);
    }

    private static byte[] readAllBytes(Context context, Uri uri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    private static Map<String, byte[]> extractAllZipEntries(byte[] zipBytes) throws Exception {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    map.put(entry.getName(), baos.toByteArray());
                }
                zis.closeEntry();
            }
        }
        return map;
    }

    private static byte[] extractZipEntry(byte[] zipBytes, String targetPath) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase(targetPath)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    return baos.toByteArray();
                }
                zis.closeEntry();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void parseRelationships(byte[] relsXmlBytes, Map<String, String> relsMap) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = db.parse(new ByteArrayInputStream(relsXmlBytes));

            NodeList list = doc.getElementsByTagName("Relationship");
            for (int i = 0; i < list.getLength(); i++) {
                Element elem = (Element) list.item(i);
                String id = getAttr(elem, "Id");
                String target = getAttr(elem, "Target");
                if (id != null && target != null) {
                    relsMap.put(id, target);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing document.xml.rels", e);
        }
    }

    private static Element findBodyElement(org.w3c.dom.Document xmlDoc) {
        NodeList bodyList = xmlDoc.getElementsByTagName("w:body");
        if (bodyList.getLength() > 0) return (Element) bodyList.item(0);
        bodyList = xmlDoc.getElementsByTagName("body");
        if (bodyList.getLength() > 0) return (Element) bodyList.item(0);
        return null;
    }

    private static boolean isTag(Node node, String local) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) return false;
        String name = node.getNodeName();
        if (name.equalsIgnoreCase(local) || name.endsWith(":" + local)) return true;
        String lName = node.getLocalName();
        return lName != null && lName.equalsIgnoreCase(local);
    }

    private static Element findFirstChild(Element parent, String local) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (isTag(c, local)) return (Element) c;
        }
        return null;
    }

    private static Element findFirstDescendant(Node parent, String local) {
        if (parent == null) return null;
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (isTag(n, local)) return (Element) n;
            Element sub = findFirstDescendant(n, local);
            if (sub != null) return sub;
        }
        return null;
    }

    private static String getAttr(Element element, String attrName) {
        if (element == null) return null;
        if (element.hasAttribute(attrName)) return element.getAttribute(attrName);
        if (element.hasAttribute("w:" + attrName)) return element.getAttribute("w:" + attrName);
        if (element.hasAttribute("r:" + attrName)) return element.getAttribute("r:" + attrName);
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node item = attrs.item(i);
                String n = item.getNodeName();
                if (n.equalsIgnoreCase(attrName) || n.endsWith(":" + attrName)) {
                    return item.getNodeValue();
                }
            }
        }
        return null;
    }

    private static DeviceRgb parseHexColor(String hex) {
        if (hex == null || hex.trim().isEmpty() || "auto".equalsIgnoreCase(hex)) return null;
        try {
            String clean = hex.trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            if (clean.length() == 6) {
                int r = Integer.parseInt(clean.substring(0, 2), 16);
                int g = Integer.parseInt(clean.substring(2, 4), 16);
                int b = Integer.parseInt(clean.substring(4, 6), 16);
                return new DeviceRgb(r, g, b);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
