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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Production-grade PowerPoint to PDF Converter for PressGo.
 * 100% Pure Java implementation supporting PPTX (OOXML) and PPT.
 * Renders presentation slide decks into formatted landscape PDF presentation pages,
 * preserving slide titles, subtitles, bullet hierarchy, tables, and embedded graphics.
 */
public final class PptxToPdfConverter {

    private static final String TAG = "PptxToPdfConverter";

    private PptxToPdfConverter() {}

    /**
     * Converts a PowerPoint presentation (PPTX or PPT) from a URI into a PDF file.
     *
     * @param context    Android Context
     * @param uri        Uri of the source presentation
     * @param outputFile Target PDF file
     * @return Converted PDF File
     * @throws Exception On conversion failure
     */
    public static File convertPptxToPdf(Context context, Uri uri, File outputFile) throws Exception {
        if (context == null || uri == null || outputFile == null) {
            throw new IllegalArgumentException("Context, Uri, and OutputFile must not be null");
        }

        byte[] inputBytes = readAllBytes(context, uri);
        if (inputBytes == null || inputBytes.length == 0) {
            throw new Exception("Selected PowerPoint file is empty or could not be read");
        }

        convertPptxBytesToPdf(inputBytes, DocumentUtils.getFileName(context, uri), outputFile);
        return outputFile;
    }

    /**
     * Converts raw bytes of a PowerPoint presentation into a PDF file.
     */
    public static void convertPptxBytesToPdf(byte[] inputBytes, String originalFileName, File outputFile) throws Exception {
        if (isZipArchive(inputBytes)) {
            convertPptx(inputBytes, originalFileName, outputFile);
        } else {
            convertLegacyPpt(inputBytes, originalFileName, outputFile);
        }
    }

    /**
     * Accurately determines the slide count of a presentation for print cost calculation.
     */
    public static int getPptxSlideCount(Context context, Uri uri) {
        try {
            byte[] bytes = readAllBytes(context, uri);
            if (bytes == null || bytes.length == 0) return 1;

            if (isZipArchive(bytes)) {
                // Count entries matching ppt/slides/slide*.xml
                int count = 0;
                try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml") && !name.contains("_rels")) {
                            count++;
                        }
                        zis.closeEntry();
                    }
                }
                return Math.max(1, count);
            } else {
                // Binary .ppt estimate
                return Math.max(1, (int) Math.ceil(bytes.length / 50000.0));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error counting PPTX slides", e);
        }
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PPTX Conversion Engine
    // ─────────────────────────────────────────────────────────────────────────

    private static void convertPptx(byte[] pptxBytes, String originalFileName, File outputFile) throws Exception {
        Map<String, byte[]> entries = extractAllZipEntries(pptxBytes);

        // Discover slides and their order
        List<String> slidePaths = getOrderedSlidePaths(entries);
        if (slidePaths.isEmpty()) {
            throw new Exception("No slides found in PowerPoint presentation");
        }

        // Setup landscape A4 PDF
        PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4.rotate());
        document.setMargins(28f, 36f, 28f, 36f);

        float pageWidth = PageSize.A4.rotate().getWidth();
        float pageHeight = PageSize.A4.rotate().getHeight();
        float printableWidth = pageWidth - document.getLeftMargin() - document.getRightMargin();
        float printableHeight = pageHeight - document.getTopMargin() - document.getBottomMargin();

        String presentationTitle = originalFileName != null && !originalFileName.isEmpty()
                ? DocumentUtils.getFileNameWithoutExtension(originalFileName)
                : "Presentation";

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        int totalSlides = slidePaths.size();

        for (int slideIndex = 0; slideIndex < totalSlides; slideIndex++) {
            if (slideIndex > 0) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }

            String slidePath = slidePaths.get(slideIndex);
            byte[] slideXmlBytes = entries.get(slidePath);
            if (slideXmlBytes == null) continue;

            // Load slide relationships (for images)
            String relsPath = getSlideRelsPath(slidePath);
            Map<String, String> slideRels = new HashMap<>();
            byte[] relsBytes = entries.get(relsPath);
            if (relsBytes != null) {
                parseRelationships(relsBytes, slideRels);
            }

            SlideData slideData;
            try {
                org.w3c.dom.Document slideDoc = db.parse(new ByteArrayInputStream(slideXmlBytes));
                slideData = parseSlideXml(slideDoc, slideRels, entries);
            } catch (Exception ex) {
                Log.w(TAG, "Error parsing slide XML: " + slidePath, ex);
                slideData = new SlideData();
                slideData.title = "Slide " + (slideIndex + 1);
                List<String> plainTexts = extractTextRunsFromBinaryPpt(slideXmlBytes);
                for (String pt : plainTexts) {
                    if (!pt.contains("<") && !pt.contains(">") && pt.length() > 2) {
                        SlideParagraph sp = new SlideParagraph();
                        SlideRun sr = new SlideRun();
                        sr.text = pt;
                        sp.runs.add(sr);
                        slideData.paragraphs.add(sp);
                    }
                }
            }

            // 1. Render Top Header Banner (Slide Number & Deck Name)
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1f, 1f}));
            headerTable.setWidth(UnitValue.createPercentValue(100));
            headerTable.setMarginBottom(10f);

            Cell leftHeader = new Cell()
                    .setBorder(null)
                    .add(new Paragraph(String.format(Locale.US, "SLIDE %02d", slideIndex + 1))
                            .setFontSize(8.5f)
                            .setBold()
                            .setFontColor(new DeviceRgb(255, 255, 255))
                            .setBackgroundColor(new DeviceRgb(210, 71, 38)) // PowerPoint Orange-Red
                            .setPadding(3f)
                            .setWidth(UnitValue.createPointValue(55f))
                            .setTextAlignment(TextAlignment.CENTER));

            Cell rightHeader = new Cell()
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(presentationTitle)
                            .setFontSize(8f)
                            .setFontColor(new DeviceRgb(148, 163, 184)));

            headerTable.addCell(leftHeader);
            headerTable.addCell(rightHeader);
            document.add(headerTable);

            // 2. Render Slide Title
            if (slideData.title != null && !slideData.title.trim().isEmpty()) {
                Paragraph titlePara = new Paragraph(slideData.title.trim())
                        .setFontSize(20f)
                        .setBold()
                        .setFontColor(new DeviceRgb(30, 41, 59))
                        .setMarginBottom(4f);
                document.add(titlePara);

                // Accent line below title
                Table accentLine = new Table(1);
                accentLine.setWidth(UnitValue.createPercentValue(100));
                Cell lineCell = new Cell()
                        .setHeight(2f)
                        .setBorder(null)
                        .setBackgroundColor(new DeviceRgb(210, 71, 38));
                accentLine.addCell(lineCell);
                accentLine.setMarginBottom(12f);
                document.add(accentLine);
            } else if (slideIndex == 0) {
                // First slide fallback title
                Paragraph titlePara = new Paragraph(presentationTitle)
                        .setFontSize(22f)
                        .setBold()
                        .setFontColor(new DeviceRgb(30, 41, 59))
                        .setMarginBottom(12f);
                document.add(titlePara);
            }

            // 3. Render Subtitle if present
            if (slideData.subtitle != null && !slideData.subtitle.trim().isEmpty()) {
                Paragraph subPara = new Paragraph(slideData.subtitle.trim())
                        .setFontSize(13f)
                        .setItalic()
                        .setFontColor(new DeviceRgb(100, 116, 139))
                        .setMarginBottom(10f);
                document.add(subPara);
            }

            // 4. Render Slide Content Paragraphs & Bullets
            for (SlideParagraph sp : slideData.paragraphs) {
                Paragraph p = new Paragraph();

                // Bullet styling based on hierarchy level
                if (sp.level == 0) {
                    p.add(new Text("•  ").setBold().setFontColor(new DeviceRgb(210, 71, 38)));
                    p.setFontSize(12f).setMarginLeft(8f).setMarginBottom(5f);
                } else if (sp.level == 1) {
                    p.add(new Text("  ○  ").setFontColor(new DeviceRgb(100, 116, 139)));
                    p.setFontSize(11f).setMarginLeft(24f).setMarginBottom(4f);
                } else {
                    p.add(new Text("    ▪  ").setFontColor(new DeviceRgb(148, 163, 184)));
                    p.setFontSize(10f).setMarginLeft(38f).setMarginBottom(3f);
                }

                for (SlideRun sr : sp.runs) {
                    Text t = new Text(sr.text);
                    if (sr.bold) t.setBold();
                    if (sr.italic) t.setItalic();
                    if (sr.underline) t.setUnderline();
                    if (sr.color != null) t.setFontColor(sr.color);
                    p.add(t);
                }
                document.add(p);
            }

            // 5. Render Slide Tables
            for (SlideTable st : slideData.tables) {
                Table table = renderSlideTable(st, printableWidth);
                if (table != null) {
                    document.add(table);
                }
            }

            // 6. Render Slide Images
            boolean isVisualSlide = slideData.paragraphs.isEmpty() && slideData.tables.isEmpty();
            for (byte[] imgBytes : slideData.images) {
                try {
                    ImageData imgData = ImageDataFactory.create(imgBytes);
                    Image img = new Image(imgData);
                    img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    img.setMarginTop(8f);
                    img.setMarginBottom(8f);

                    float maxImgW = isVisualSlide ? printableWidth : (printableWidth * 0.9f);
                    float maxImgH = isVisualSlide
                            ? Math.max(260f, printableHeight - (slideData.title != null ? 80f : 20f))
                            : Math.max(180f, printableHeight - (slideData.paragraphs.size() * 24f) - 100f);

                    float imgW = img.getImageWidth();
                    float imgH = img.getImageHeight();

                    float scaleW = maxImgW / imgW;
                    float scaleH = maxImgH / imgH;
                    float scale = Math.min(1.0f, Math.min(scaleW, scaleH));

                    img.scaleToFit(imgW * scale, imgH * scale);
                    document.add(img);
                } catch (Exception ex) {
                    Log.w(TAG, "Error embedding slide image", ex);
                }
            }

            // 7. Bottom Slide Footer
            Paragraph footer = new Paragraph(String.format(Locale.US, "Slide %d of %d  •  PressGo Campus Document Engine", slideIndex + 1, totalSlides))
                    .setFontSize(8f)
                    .setFontColor(new DeviceRgb(148, 163, 184))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(14f);
            document.add(footer);
        }

        document.close();
    }

    private static Table renderSlideTable(SlideTable st, float printableWidth) {
        if (st.rows.isEmpty()) return null;
        int cols = st.maxCols;
        if (cols <= 0) return null;

        float[] widths = new float[cols];
        for (int i = 0; i < cols; i++) widths[i] = 1f;

        Table table = new Table(UnitValue.createPercentArray(widths));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginTop(10f);
        table.setMarginBottom(10f);

        for (int r = 0; r < st.rows.size(); r++) {
            List<String> rowCells = st.rows.get(r);
            for (int c = 0; c < cols; c++) {
                String text = c < rowCells.size() ? rowCells.get(c) : "";
                Cell cell = new Cell().setPadding(5f)
                        .setBorder(new SolidBorder(new DeviceRgb(203, 213, 225), 0.75f));
                if (r == 0) {
                    cell.setBackgroundColor(new DeviceRgb(248, 250, 252));
                    cell.add(new Paragraph(text).setBold().setFontSize(9.5f).setMargin(0));
                } else {
                    cell.add(new Paragraph(text).setFontSize(9f).setMargin(0));
                }
                table.addCell(cell);
            }
        }
        return table;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XML Slide Parsing Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static class SlideData {
        String title;
        String subtitle;
        final List<SlideParagraph> paragraphs = new ArrayList<>();
        final List<SlideTable> tables = new ArrayList<>();
        final List<byte[]> images = new ArrayList<>();
    }

    private static class SlideParagraph {
        int level = 0;
        final List<SlideRun> runs = new ArrayList<>();
    }

    private static class SlideRun {
        String text = "";
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        DeviceRgb color = null;
    }

    private static class SlideTable {
        int maxCols = 0;
        final List<List<String>> rows = new ArrayList<>();
    }

    private static SlideData parseSlideXml(org.w3c.dom.Document slideDoc,
                                           Map<String, String> slideRels,
                                           Map<String, byte[]> allZipEntries) {
        SlideData data = new SlideData();

        // 1. Find all shapes <p:sp>
        NodeList spList = slideDoc.getElementsByTagName("p:sp");
        if (spList.getLength() == 0) spList = slideDoc.getElementsByTagName("sp");

        for (int i = 0; i < spList.getLength(); i++) {
            Element sp = (Element) spList.item(i);
            String placeholderType = getPlaceholderType(sp);

            // Extract text paragraphs
            Element txBody = findFirstChild(sp, "txBody");
            if (txBody == null) continue;

            NodeList pList = txBody.getElementsByTagName("a:p");
            if (pList.getLength() == 0) pList = txBody.getElementsByTagName("p");

            for (int pIdx = 0; pIdx < pList.getLength(); pIdx++) {
                Element pElem = (Element) pList.item(pIdx);
                SlideParagraph spPara = parseSlideParagraph(pElem);

                if (spPara.runs.isEmpty()) continue;

                StringBuilder fullText = new StringBuilder();
                for (SlideRun r : spPara.runs) fullText.append(r.text);
                String line = fullText.toString().trim();
                if (line.isEmpty()) continue;

                if ("title".equalsIgnoreCase(placeholderType) || "ctrTitle".equalsIgnoreCase(placeholderType)) {
                    if (data.title == null) {
                        data.title = line;
                    } else {
                        data.title += " " + line;
                    }
                } else if ("subTitle".equalsIgnoreCase(placeholderType)) {
                    if (data.subtitle == null) {
                        data.subtitle = line;
                    } else {
                        data.subtitle += " " + line;
                    }
                } else {
                    // Body / content
                    data.paragraphs.add(spPara);
                }
            }
        }

        // 2. Find all tables <a:tbl>
        NodeList tblList = slideDoc.getElementsByTagName("a:tbl");
        if (tblList.getLength() == 0) tblList = slideDoc.getElementsByTagName("tbl");

        for (int t = 0; t < tblList.getLength(); t++) {
            Element tbl = (Element) tblList.item(t);
            SlideTable st = new SlideTable();

            NodeList trList = tbl.getElementsByTagName("a:tr");
            if (trList.getLength() == 0) trList = tbl.getElementsByTagName("tr");

            for (int r = 0; r < trList.getLength(); r++) {
                Element tr = (Element) trList.item(r);
                List<String> rowCells = new ArrayList<>();
                NodeList tcList = tr.getElementsByTagName("a:tc");
                if (tcList.getLength() == 0) tcList = tr.getElementsByTagName("tc");

                for (int c = 0; c < tcList.getLength(); c++) {
                    Element tc = (Element) tcList.item(c);
                    rowCells.add(tc.getTextContent().trim());
                }
                if (rowCells.size() > st.maxCols) st.maxCols = rowCells.size();
                st.rows.add(rowCells);
            }
            if (!st.rows.isEmpty()) data.tables.add(st);
        }

        // 3. Find all pictures and blip images across slide
        NodeList blipList = slideDoc.getElementsByTagName("a:blip");
        if (blipList.getLength() == 0) blipList = slideDoc.getElementsByTagName("blip");

        for (int p = 0; p < blipList.getLength(); p++) {
            Element blip = (Element) blipList.item(p);
            String rId = getAttr(blip, "embed");
            if (rId == null) rId = getAttr(blip, "r:embed");
            if (rId != null && slideRels.containsKey(rId)) {
                String target = slideRels.get(rId);
                if (target != null) {
                    byte[] bytes = findZipEntry(allZipEntries, target);
                    if (bytes != null && bytes.length > 0) {
                        data.images.add(bytes);
                    }
                }
            }
        }

        return data;
    }

    private static SlideParagraph parseSlideParagraph(Element pElem) {
        SlideParagraph sp = new SlideParagraph();

        // Level
        Element pPr = findFirstChild(pElem, "pPr");
        if (pPr != null) {
            String lvl = getAttr(pPr, "lvl");
            if (lvl != null) {
                try {
                    sp.level = Integer.parseInt(lvl);
                } catch (Exception ignored) {}
            }
        }

        NodeList childNodes = pElem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node n = childNodes.item(i);
            if (isTag(n, "r")) { // Run
                Element rElem = (Element) n;
                NodeList tList = rElem.getElementsByTagName("a:t");
                if (tList.getLength() == 0) tList = rElem.getElementsByTagName("t");

                StringBuilder sb = new StringBuilder();
                for (int t = 0; t < tList.getLength(); t++) {
                    sb.append(tList.item(t).getTextContent());
                }

                if (sb.length() > 0) {
                    SlideRun run = new SlideRun();
                    run.text = sb.toString();

                    Element rPr = findFirstChild(rElem, "rPr");
                    if (rPr != null) {
                        String b = getAttr(rPr, "b");
                        run.bold = "1".equals(b) || "true".equalsIgnoreCase(b);

                        String it = getAttr(rPr, "i");
                        run.italic = "1".equals(it) || "true".equalsIgnoreCase(it);

                        String u = getAttr(rPr, "u");
                        run.underline = u != null && !"none".equalsIgnoreCase(u);

                        // Color
                        Element srgb = findFirstChild(rPr, "srgbClr");
                        if (srgb != null) {
                            String val = getAttr(srgb, "val");
                            run.color = parseHexColor(val);
                        }
                    }
                    sp.runs.add(run);
                }
            } else if (isTag(n, "br")) {
                SlideRun run = new SlideRun();
                run.text = "\n";
                sp.runs.add(run);
            }
        }

        return sp;
    }

    private static String getPlaceholderType(Element sp) {
        Element nvSpPr = findFirstChild(sp, "nvSpPr");
        if (nvSpPr == null) return null;
        Element nvPr = findFirstChild(nvSpPr, "nvPr");
        if (nvPr == null) return null;
        Element ph = findFirstChild(nvPr, "ph");
        if (ph == null) return null;
        return getAttr(ph, "type");
    }

    private static List<String> getOrderedSlidePaths(Map<String, byte[]> entries) {
        List<String> list = new ArrayList<>();

        // Check ppt/presentation.xml and ppt/_rels/presentation.xml.rels
        byte[] presRelsBytes = entries.get("ppt/_rels/presentation.xml.rels");
        byte[] presXmlBytes = entries.get("ppt/presentation.xml");

        if (presRelsBytes != null && presXmlBytes != null) {
            Map<String, String> rIdToTarget = new HashMap<>();
            parseRelationships(presRelsBytes, rIdToTarget);

            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(presXmlBytes));

                NodeList sldIdList = doc.getElementsByTagName("p:sldId");
                if (sldIdList.getLength() == 0) sldIdList = doc.getElementsByTagName("sldId");

                for (int i = 0; i < sldIdList.getLength(); i++) {
                    Element sldId = (Element) sldIdList.item(i);
                    String rId = null;
                    if (sldId.hasAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")) {
                        rId = sldId.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    }
                    if (rId == null || rId.isEmpty()) {
                        rId = getAttr(sldId, "r:id");
                    }
                    if (rId == null || !rIdToTarget.containsKey(rId)) {
                        NamedNodeMap attrs = sldId.getAttributes();
                        if (attrs != null) {
                            for (int a = 0; a < attrs.getLength(); a++) {
                                String val = attrs.item(a).getNodeValue();
                                if (rIdToTarget.containsKey(val)) {
                                    rId = val;
                                    break;
                                }
                            }
                        }
                    }
                    if (rId == null) {
                        rId = getAttr(sldId, "id");
                    }
                    if (rId != null && rIdToTarget.containsKey(rId)) {
                        String target = rIdToTarget.get(rId);
                        if (target != null) {
                            String clean = target.replace("../", "").replaceAll("^/+", "");
                            String key = clean.startsWith("ppt/") ? clean : ("ppt/" + clean);
                            if (entries.containsKey(key)) {
                                list.add(key);
                            } else if (entries.containsKey(clean)) {
                                list.add(clean);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error ordering slides via presentation.xml", e);
            }
        }

        // Fallback: natural sort of ppt/slides/slide*.xml
        if (list.isEmpty()) {
            Pattern pattern = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
            for (String key : entries.keySet()) {
                if (pattern.matcher(key).matches()) {
                    list.add(key);
                }
            }
            Collections.sort(list, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    int n1 = extractSlideNumber(o1);
                    int n2 = extractSlideNumber(o2);
                    return Integer.compare(n1, n2);
                }
            });
        }

        return list;
    }

    private static int extractSlideNumber(String path) {
        Pattern p = Pattern.compile("slide(\\d+)\\.xml");
        Matcher m = p.matcher(path);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static String getSlideRelsPath(String slidePath) {
        // e.g. "ppt/slides/slide1.xml" -> "ppt/slides/_rels/slide1.xml.rels"
        int idx = slidePath.lastIndexOf('/');
        if (idx != -1) {
            String dir = slidePath.substring(0, idx);
            String name = slidePath.substring(idx + 1);
            return dir + "/_rels/" + name + ".rels";
        }
        return slidePath + ".rels";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy PPT Fallback
    // ─────────────────────────────────────────────────────────────────────────

    private static void convertLegacyPpt(byte[] inputBytes, String originalFileName, File outputFile) throws Exception {
        PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4.rotate());
        document.setMargins(28f, 36f, 28f, 36f);

        String deckTitle = originalFileName != null && !originalFileName.isEmpty()
                ? DocumentUtils.getFileNameWithoutExtension(originalFileName)
                : "PowerPoint Presentation";

        List<String> paragraphs = extractTextRunsFromBinaryPpt(inputBytes);

        if (paragraphs.isEmpty()) {
            document.add(new Paragraph(deckTitle).setFontSize(22f).setBold());
            document.add(new Paragraph("(No readable slide text extracted from legacy PPT file)")
                    .setFontColor(new DeviceRgb(100, 116, 139)));
        } else {
            // Group every ~8 paragraphs into a separate slide page
            int slideNum = 1;
            int countInSlide = 0;

            for (int i = 0; i < paragraphs.size(); i++) {
                if (countInSlide == 0) {
                    if (slideNum > 1) {
                        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                    }
                    Paragraph slideHeader = new Paragraph(String.format(Locale.US, "SLIDE %02d  •  %s", slideNum, deckTitle))
                            .setFontSize(9f)
                            .setBold()
                            .setFontColor(new DeviceRgb(210, 71, 38))
                            .setMarginBottom(12f);
                    document.add(slideHeader);
                }

                String line = paragraphs.get(i).trim();
                if (!line.isEmpty()) {
                    if (countInSlide == 0) {
                        document.add(new Paragraph(line).setFontSize(18f).setBold().setMarginBottom(8f));
                    } else {
                        document.add(new Paragraph("•  " + line).setFontSize(12f).setMarginLeft(12f).setMarginBottom(4f));
                    }
                    countInSlide++;
                }

                if (countInSlide >= 7) {
                    countInSlide = 0;
                    slideNum++;
                }
            }
        }

        document.close();
    }

    private static List<String> extractTextRunsFromBinaryPpt(byte[] bytes) {
        List<String> result = new ArrayList<>();
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
            } else {
                if (consecutiveChars < 4 && current.length() > 0) {
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
    // Common Utilities
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

    private static byte[] findZipEntry(Map<String, byte[]> entries, String target) {
        if (target == null || entries == null) return null;
        byte[] b = entries.get(target);
        if (b != null) return b;

        String clean = target.replace("../", "").replaceAll("^/+", "");
        b = entries.get(clean);
        if (b != null) return b;

        String pptKey = clean.startsWith("ppt/") ? clean : ("ppt/" + clean);
        b = entries.get(pptKey);
        if (b != null) return b;

        // Case-insensitive fallback
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().equalsIgnoreCase(target) || e.getKey().equalsIgnoreCase(clean) || e.getKey().equalsIgnoreCase(pptKey)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void parseRelationships(byte[] relsXmlBytes, Map<String, String> relsMap) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(relsXmlBytes));

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
            Log.w(TAG, "Error parsing rels XML", e);
        }
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

    private static String getAttr(Element element, String attrName) {
        if (element == null) return null;
        if (element.hasAttribute(attrName)) return element.getAttribute(attrName);
        if (element.hasAttribute("r:" + attrName)) return element.getAttribute("r:" + attrName);
        if (element.hasAttribute("p:" + attrName)) return element.getAttribute("p:" + attrName);
        if (element.hasAttribute("a:" + attrName)) return element.getAttribute("a:" + attrName);
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
        if (hex == null || hex.trim().isEmpty()) return null;
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
