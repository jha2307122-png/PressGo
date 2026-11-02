package com.pressgo.app;

import com.pressgo.app.converters.PdfToPptxConverter;
import com.pressgo.app.converters.PdfToWordConverter;
import com.pressgo.app.converters.PptxToPdfConverter;
import com.pressgo.app.converters.WordToPdfConverter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests verifying WordToPdfConverter, PptxToPdfConverter,
 * PdfToWordConverter, and PdfToPptxConverter (Week 11).
 * Tests text, formatting, OpenXML validation, and embedded image extraction.
 */
public class DocumentConverterTest {

    private File tempDir;

    // 1x1 valid sample GIF image byte array
    private static final byte[] VALID_GIF_BYTES = new byte[] {
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x21, (byte) 0xF9, 0x04,
        0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00,
        0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    };

    @Before
    public void setUp() {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "pressgo_test_" + System.currentTimeMillis());
        tempDir.mkdirs();
    }

    @After
    public void tearDown() {
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testDocxToPdfConversion() throws Exception {
        byte[] docxBytes = createMockDocxZip(
                "CS401 Software Engineering Project",
                "Requirements Specification Document",
                "This document describes the architectural specifications for the PressGo campus printing system."
        );

        File outputFile = new File(tempDir, "output_doc.pdf");
        WordToPdfConverter.convertWordBytesToPdf(docxBytes, "CS401_Specification.docx", outputFile);

        Assert.assertTrue("PDF output file must exist", outputFile.exists());
        Assert.assertTrue("PDF output file must not be empty", outputFile.length() > 500);
    }

    @Test
    public void testDocxToPdfWithImages() throws Exception {
        byte[] docxBytes = createMockDocxWithImageZip();
        File outputFile = new File(tempDir, "output_doc_with_img.pdf");
        WordToPdfConverter.convertWordBytesToPdf(docxBytes, "Specification_With_Images.docx", outputFile);

        Assert.assertTrue("PDF output file must exist", outputFile.exists());
        Assert.assertTrue("PDF output file must not be empty", outputFile.length() > 500);
    }

    @Test
    public void testLegacyDocOrRtfConversion() throws Exception {
        String rtfContent = "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Courier;}}\n" +
                "\\b PressGo Campus Printing System \\b0\\par\n" +
                "Automated document conversion test.\\par\n" +
                "Submitted for Week 11 evaluation.\\par}";
        byte[] rtfBytes = rtfContent.getBytes(StandardCharsets.ISO_8859_1);

        File outputFile = new File(tempDir, "output_legacy_doc.pdf");
        WordToPdfConverter.convertWordBytesToPdf(rtfBytes, "legacy_notes.doc", outputFile);

        Assert.assertTrue("Legacy DOC PDF output must exist", outputFile.exists());
        Assert.assertTrue("Legacy DOC PDF output must not be empty", outputFile.length() > 300);
    }

    @Test
    public void testPptxToPdfConversion() throws Exception {
        byte[] pptxBytes = createMockPptxZip(
                "PressGo: Android Architecture",
                "Final Defense Presentation",
                new String[]{
                        "Module 8: Activity Lifecycle & Layouts",
                        "Module 9: RecyclerViews & Adapters",
                        "Module 10: Services & Content Providers",
                        "Module 11: Document Converters & Firebase"
                }
        );

        File outputFile = new File(tempDir, "output_deck.pdf");
        PptxToPdfConverter.convertPptxBytesToPdf(pptxBytes, "PressGo_Defense.pptx", outputFile);

        Assert.assertTrue("PPTX PDF output must exist", outputFile.exists());
        Assert.assertTrue("PPTX PDF output must not be empty", outputFile.length() > 500);
    }

    @Test
    public void testPptxToPdfWithImages() throws Exception {
        byte[] pptxBytes = createMockPptxWithImageZip();
        File outputFile = new File(tempDir, "output_deck_with_img.pdf");
        PptxToPdfConverter.convertPptxBytesToPdf(pptxBytes, "Deck_With_Images.pptx", outputFile);

        Assert.assertTrue("PPTX PDF output must exist", outputFile.exists());
        Assert.assertTrue("PPTX PDF output must not be empty", outputFile.length() > 500);
    }

    @Test
    public void testLegacyPptConversion() throws Exception {
        String mockPptStream = "PressGo Presentation Slide\n" +
                "Introduction to Android Services\n" +
                "Foreground conversion services\n" +
                "Broadcast receivers\n" +
                "WorkManager tasks";
        byte[] pptBytes = mockPptStream.getBytes(StandardCharsets.UTF_8);

        File outputFile = new File(tempDir, "output_legacy_ppt.pdf");
        PptxToPdfConverter.convertPptxBytesToPdf(pptBytes, "slides.ppt", outputFile);

        Assert.assertTrue("Legacy PPT PDF output must exist", outputFile.exists());
        Assert.assertTrue("Legacy PPT PDF output must not be empty", outputFile.length() > 300);
    }

    @Test
    public void testPdfToWordConversion() throws Exception {
        byte[] pdfBytes = createSamplePdf(false);
        File outputFile = new File(tempDir, "output_doc.docx");
        PdfToWordConverter.convertPdfBytesToWord(pdfBytes, "test_document.pdf", outputFile);

        Assert.assertTrue("DOCX output file must exist", outputFile.exists());
        Assert.assertTrue("DOCX output file must not be empty", outputFile.length() > 500);

        boolean hasDocumentXml = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputFile))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(ze.getName())) hasDocumentXml = true;
                zis.closeEntry();
            }
        }
        Assert.assertTrue("Generated DOCX must contain word/document.xml", hasDocumentXml);
    }

    @Test
    public void testPdfToWordWithImages() throws Exception {
        byte[] pdfBytes = createSamplePdf(true);
        File outputFile = new File(tempDir, "output_doc_with_images.docx");
        PdfToWordConverter.convertPdfBytesToWord(pdfBytes, "presentation.pdf", outputFile);

        Assert.assertTrue("DOCX output file must exist", outputFile.exists());

        boolean hasMediaImage = false;
        boolean hasDocRels = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputFile))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().startsWith("word/media/image_")) hasMediaImage = true;
                if ("word/_rels/document.xml.rels".equals(ze.getName())) hasDocRels = true;
                zis.closeEntry();
            }
        }
        Assert.assertTrue("Generated DOCX must contain extracted image in word/media/", hasMediaImage);
        Assert.assertTrue("Generated DOCX must contain word/_rels/document.xml.rels", hasDocRels);
    }

    @Test
    public void testPdfToPptxConversion() throws Exception {
        byte[] pdfBytes = createSamplePdf(false);
        File outputFile = new File(tempDir, "output_slides.pptx");
        PdfToPptxConverter.convertPdfBytesToPptx(pdfBytes, "test_presentation.pdf", outputFile);

        Assert.assertTrue("PPTX output file must exist", outputFile.exists());
        Assert.assertTrue("PPTX output file must not be empty", outputFile.length() > 500);

        boolean hasPresXml = false;
        boolean hasSlide1 = false;
        boolean hasSlide2 = false;
        boolean hasSlideMaster = false;
        boolean hasSlideLayout = false;
        boolean hasTheme = false;
        boolean hasMasterRels = false;
        boolean hasLayoutRels = false;
        boolean hasPresRels = false;
        boolean hasClrMapOvrInSlide = false;

        byte[] buf = new byte[4096];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputFile))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();
                if ("ppt/presentation.xml".equals(name)) hasPresXml = true;
                if ("ppt/slideMasters/slideMaster1.xml".equals(name)) hasSlideMaster = true;
                if ("ppt/slideLayouts/slideLayout1.xml".equals(name)) hasSlideLayout = true;
                if ("ppt/theme/theme1.xml".equals(name)) hasTheme = true;
                if ("ppt/_rels/presentation.xml.rels".equals(name)) hasPresRels = true;
                if ("ppt/slideMasters/_rels/slideMaster1.xml.rels".equals(name)) hasMasterRels = true;
                if ("ppt/slideLayouts/_rels/slideLayout1.xml.rels".equals(name)) hasLayoutRels = true;
                if ("ppt/slides/slide1.xml".equals(name)) {
                    hasSlide1 = true;
                    ByteArrayOutputStream sBaos = new ByteArrayOutputStream();
                    int n;
                    while ((n = zis.read(buf)) > 0) sBaos.write(buf, 0, n);
                    String s1Content = new String(sBaos.toByteArray(), StandardCharsets.UTF_8);
                    if (s1Content.contains("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>")) {
                        hasClrMapOvrInSlide = true;
                    }
                }
                if ("ppt/slides/slide2.xml".equals(name)) hasSlide2 = true;
                zis.closeEntry();
            }
        }
        Assert.assertTrue("Generated PPTX must contain ppt/presentation.xml", hasPresXml);
        Assert.assertTrue("Generated PPTX must contain ppt/slideMasters/slideMaster1.xml", hasSlideMaster);
        Assert.assertTrue("Generated PPTX must contain ppt/slideLayouts/slideLayout1.xml", hasSlideLayout);
        Assert.assertTrue("Generated PPTX must contain ppt/theme/theme1.xml", hasTheme);
        Assert.assertTrue("Generated PPTX must contain ppt/_rels/presentation.xml.rels", hasPresRels);
        Assert.assertTrue("Generated PPTX must contain slideMaster rels", hasMasterRels);
        Assert.assertTrue("Generated PPTX must contain slideLayout rels", hasLayoutRels);
        Assert.assertTrue("Generated PPTX must contain ppt/slides/slide1.xml", hasSlide1);
        Assert.assertTrue("Generated PPTX must contain ppt/slides/slide2.xml", hasSlide2);
        Assert.assertTrue("Slide 1 must include master color map override", hasClrMapOvrInSlide);
    }

    @Test
    public void testPdfToPptxRoundTripToPdf() throws Exception {
        byte[] pdfBytes = createSamplePdf(false);
        File pptxFile = new File(tempDir, "roundtrip.pptx");
        PdfToPptxConverter.convertPdfBytesToPptx(pdfBytes, "roundtrip_test.pdf", pptxFile);

        Assert.assertTrue("Generated PPTX must exist", pptxFile.exists());

        // Read the generated PPTX bytes and convert back to PDF using PptxToPdfConverter
        byte[] pptxBytes = new byte[(int) pptxFile.length()];
        try (FileInputStream fis = new FileInputStream(pptxFile)) {
            int read = 0;
            while (read < pptxBytes.length) {
                int r = fis.read(pptxBytes, read, pptxBytes.length - read);
                if (r == -1) break;
                read += r;
            }
        }

        File roundtripPdf = new File(tempDir, "roundtrip_result.pdf");
        PptxToPdfConverter.convertPptxBytesToPdf(pptxBytes, "roundtrip.pptx", roundtripPdf);

        Assert.assertTrue("Round-trip PDF must exist", roundtripPdf.exists());
        Assert.assertTrue("Round-trip PDF must not be empty", roundtripPdf.length() > 500);
    }

    @Test
    public void testPdfToPptxWithImages() throws Exception {
        byte[] pdfBytes = createSamplePdf(true);
        File outputFile = new File(tempDir, "output_slides_with_images.pptx");
        PdfToPptxConverter.convertPdfBytesToPptx(pdfBytes, "visual_deck.pdf", outputFile);

        Assert.assertTrue("PPTX output file must exist", outputFile.exists());

        boolean hasMediaSlide = false;
        boolean hasSlideRels = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputFile))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().startsWith("ppt/media/slide_")) hasMediaSlide = true;
                if ("ppt/slides/_rels/slide1.xml.rels".equals(ze.getName())) hasSlideRels = true;
                zis.closeEntry();
            }
        }
        Assert.assertTrue("Generated PPTX must contain media file in ppt/media/", hasMediaSlide);
        Assert.assertTrue("Generated PPTX must contain slide rels linking media", hasSlideRels);
    }

    private byte[] createSamplePdf(boolean includeImage) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
        com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdfDoc);

        // Page 1
        doc.add(new com.itextpdf.layout.element.Paragraph("PressGo Project Overview").setFontSize(18f).setBold());
        doc.add(new com.itextpdf.layout.element.Paragraph("This is the main introduction to the document conversion system."));
        doc.add(new com.itextpdf.layout.element.Paragraph("• Fast on-device conversion"));
        doc.add(new com.itextpdf.layout.element.Paragraph("• 100% pure Java implementation"));

        if (includeImage) {
            byte[] rawPixels = new byte[20 * 20 * 3];
            for (int i = 0; i < rawPixels.length; i += 3) {
                rawPixels[i] = (byte) 30;     // R
                rawPixels[i + 1] = (byte) 144; // G
                rawPixels[i + 2] = (byte) 255; // B
            }
            com.itextpdf.io.image.ImageData imgData = com.itextpdf.io.image.ImageDataFactory.create(20, 20, 3, 8, rawPixels, null);
            com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(imgData);
            doc.add(img);
        }

        // Page 2
        doc.add(new com.itextpdf.layout.element.AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE));
        doc.add(new com.itextpdf.layout.element.Paragraph("Module 11 Specifications").setFontSize(18f).setBold());
        doc.add(new com.itextpdf.layout.element.Paragraph("Detailed breakdown of XML parsing, Services, and PDF/Office conversions."));

        doc.close();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mock Generators
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] createMockDocxZip(String heading, String title, String bodyText) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                    "</Types>";
            zos.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String docXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
                    "  <w:body>\n" +
                    "    <w:p>\n" +
                    "      <w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>\n" +
                    "      <w:r><w:rPr><w:b/></w:rPr><w:t>" + heading + "</w:t></w:r>\n" +
                    "    </w:p>\n" +
                    "    <w:p>\n" +
                    "      <w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>\n" +
                    "      <w:r><w:rPr><w:i/></w:rPr><w:t>" + title + "</w:t></w:r>\n" +
                    "    </w:p>\n" +
                    "    <w:p>\n" +
                    "      <w:r><w:t>" + bodyText + "</w:t></w:r>\n" +
                    "    </w:p>\n" +
                    "    <w:tbl>\n" +
                    "      <w:tr>\n" +
                    "        <w:tc><w:p><w:r><w:t>Feature</w:t></w:r></w:p></w:tc>\n" +
                    "        <w:tc><w:p><w:r><w:t>Status</w:t></w:r></w:p></w:tc>\n" +
                    "      </w:tr>\n" +
                    "      <w:tr>\n" +
                    "        <w:tc><w:p><w:r><w:t>Word to PDF</w:t></w:r></w:p></w:tc>\n" +
                    "        <w:tc><w:p><w:r><w:t>Operational</w:t></w:r></w:p></w:tc>\n" +
                    "      </w:tr>\n" +
                    "    </w:tbl>\n" +
                    "  </w:body>\n" +
                    "</w:document>";
            zos.write(docXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createMockPptxZip(String slide1Title, String slide1Sub, String[] slide2Bullets) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "</Types>";
            zos.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/presentation.xml
            zos.putNextEntry(new ZipEntry("ppt/presentation.xml"));
            String presXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <p:sldIdLst>\n" +
                    "    <p:sldId id=\"256\" r:id=\"rId1\"/>\n" +
                    "    <p:sldId id=\"257\" r:id=\"rId2\"/>\n" +
                    "  </p:sldIdLst>\n" +
                    "</p:presentation>";
            zos.write(presXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/_rels/presentation.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/_rels/presentation.xml.rels"));
            String presRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/>\n" +
                    "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide2.xml\"/>\n" +
                    "</Relationships>";
            zos.write(presRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/slides/slide1.xml
            zos.putNextEntry(new ZipEntry("ppt/slides/slide1.xml"));
            String s1Xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">\n" +
                    "  <p:cSld>\n" +
                    "    <p:spTree>\n" +
                    "      <p:sp>\n" +
                    "        <p:nvSpPr><p:nvPr><p:ph type=\"title\"/></p:nvPr></p:nvSpPr>\n" +
                    "        <p:txBody><a:p><a:r><a:t>" + slide1Title + "</a:t></a:r></a:p></p:txBody>\n" +
                    "      </p:sp>\n" +
                    "      <p:sp>\n" +
                    "        <p:nvSpPr><p:nvPr><p:ph type=\"subTitle\"/></p:nvPr></p:nvSpPr>\n" +
                    "        <p:txBody><a:p><a:r><a:t>" + slide1Sub + "</a:t></a:r></a:p></p:txBody>\n" +
                    "      </p:sp>\n" +
                    "    </p:spTree>\n" +
                    "  </p:cSld>\n" +
                    "</p:sld>";
            zos.write(s1Xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/slides/slide2.xml
            zos.putNextEntry(new ZipEntry("ppt/slides/slide2.xml"));
            StringBuilder s2Xml = new StringBuilder();
            s2Xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                    .append("<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">\n")
                    .append("  <p:cSld><p:spTree>\n")
                    .append("    <p:sp>\n")
                    .append("      <p:nvSpPr><p:nvPr><p:ph type=\"title\"/></p:nvPr></p:nvSpPr>\n")
                    .append("      <p:txBody><a:p><a:r><a:t>Core Project Modules</a:t></a:r></a:p></p:txBody>\n")
                    .append("    </p:sp>\n")
                    .append("    <p:sp><p:txBody>\n");
            for (String b : slide2Bullets) {
                String escaped = b.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                s2Xml.append("      <a:p><a:pPr lvl=\"0\"/><a:r><a:t>").append(escaped).append("</a:t></a:r></a:p>\n");
            }
            s2Xml.append("    </p:txBody></p:sp>\n")
                    .append("  </p:spTree></p:cSld>\n")
                    .append("</p:sld>");
            zos.write(s2Xml.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createMockDocxWithImageZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String ct = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Default Extension=\"gif\" ContentType=\"image/gif\"/>\n" +
                    "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                    "</Types>";
            zos.write(ct.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                    "</Relationships>";
            zos.write(rootRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // word/_rels/document.xml.rels
            zos.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            String docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rIdImg1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.gif\"/>\n" +
                    "</Relationships>";
            zos.write(docRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // word/media/image1.gif
            zos.putNextEntry(new ZipEntry("word/media/image1.gif"));
            zos.write(VALID_GIF_BYTES);
            zos.closeEntry();

            // word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String docXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">\n" +
                    "  <w:body>\n" +
                    "    <w:p><w:r><w:t>Project Architecture</w:t></w:r></w:p>\n" +
                    "    <w:p><w:r><w:drawing><a:blip r:embed=\"rIdImg1\"/></w:drawing></w:r></w:p>\n" +
                    "  </w:body>\n" +
                    "</w:document>";
            zos.write(docXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createMockPptxWithImageZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            String ct = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Default Extension=\"gif\" ContentType=\"image/gif\"/>\n" +
                    "  <Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>\n" +
                    "  <Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n" +
                    "</Types>";
            zos.write(ct.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>\n" +
                    "</Relationships>";
            zos.write(rootRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/presentation.xml
            zos.putNextEntry(new ZipEntry("ppt/presentation.xml"));
            String presXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <p:sldIdLst><p:sldId id=\"256\" r:id=\"rId1\"/></p:sldIdLst>\n" +
                    "</p:presentation>";
            zos.write(presXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/_rels/presentation.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/_rels/presentation.xml.rels"));
            String presRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/>\n" +
                    "</Relationships>";
            zos.write(presRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/media/image1.gif
            zos.putNextEntry(new ZipEntry("ppt/media/image1.gif"));
            zos.write(VALID_GIF_BYTES);
            zos.closeEntry();

            // ppt/slides/_rels/slide1.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/slides/_rels/slide1.xml.rels"));
            String sRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rIdImg1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image1.gif\"/>\n" +
                    "</Relationships>";
            zos.write(sRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // ppt/slides/slide1.xml
            zos.putNextEntry(new ZipEntry("ppt/slides/slide1.xml"));
            String s1 = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <p:cSld><p:spTree>\n" +
                    "    <p:sp><p:nvSpPr><p:nvPr><p:ph type=\"title\"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Visual Slide</a:t></a:r></a:p></p:txBody></p:sp>\n" +
                    "    <p:pic><p:blipFill><a:blip r:embed=\"rIdImg1\"/></p:blipFill></p:pic>\n" +
                    "  </p:spTree></p:cSld>\n" +
                    "</p:sld>";
            zos.write(s1.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
