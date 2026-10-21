package com.pressgo.app.converters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.pressgo.app.utils.DocumentUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Production-grade PDF to PowerPoint (.pptx) Converter for PressGo.
 * 100% Pure Java implementation transforming PDF pages into standard 16:9 widescreen presentation slides.
 * Renders high-fidelity page visuals, preserves embedded images, and structures extracted text into OpenXML slides.
 */
public final class PdfToPptxConverter {

    private static final String TAG = "PdfToPptxConverter";

    // 16:9 Widescreen slide canvas dimensions in EMUs (13.33 in x 7.5 in)
    private static final long SLIDE_WIDTH_EMU = 12192000L;
    private static final long SLIDE_HEIGHT_EMU = 6858000L;
    private static final double SLIDE_ASPECT_RATIO = (double) SLIDE_WIDTH_EMU / (double) SLIDE_HEIGHT_EMU; // ~1.7778

    private PdfToPptxConverter() {}

    /**
     * Converts a PDF file from a URI into an editable PowerPoint presentation (.pptx).
     *
     * @param context    Android Context
     * @param uri        Uri of the source PDF
     * @param outputFile Target .pptx file
     * @return Converted PowerPoint File
     * @throws Exception On conversion failure
     */
    public static File convertPdfToPptx(Context context, Uri uri, File outputFile) throws Exception {
        if (context == null || uri == null || outputFile == null) {
            throw new IllegalArgumentException("Context, Uri, and OutputFile must not be null");
        }

        byte[] pdfBytes = readAllBytes(context, uri);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new Exception("Selected PDF file is empty or could not be read");
        }

        convertPdfBytesToPptx(context, pdfBytes, DocumentUtils.getFileName(context, uri), outputFile);
        return outputFile;
    }

    /**
     * Converts raw bytes of a PDF file into an editable PowerPoint presentation (.pptx) without Context.
     */
    public static void convertPdfBytesToPptx(byte[] pdfBytes, String originalFileName, File outputFile) throws Exception {
        convertPdfBytesToPptx(null, pdfBytes, originalFileName, outputFile);
    }

    /**
     * Converts raw bytes of a PDF file into an editable PowerPoint presentation (.pptx).
     */
    public static void convertPdfBytesToPptx(Context context, byte[] pdfBytes, String originalFileName, File outputFile) throws Exception {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF byte array must not be null or empty");
        }

        PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
        PdfDocument pdfDoc = new PdfDocument(reader);
        int pageCount = pdfDoc.getNumberOfPages();

        if (pageCount <= 0) {
            pdfDoc.close();
            reader.close();
            throw new Exception("PDF document has 0 pages");
        }

        // 1. Attempt high-fidelity page rendering via PdfRenderer (if Android graphics available)
        List<byte[]> renderedSlides = renderPdfSlidesWithRenderer(context, pdfBytes, pageCount);

        List<SlideContent> slides = new ArrayList<>();
        for (int p = 1; p <= pageCount; p++) {
            PdfPage page = pdfDoc.getPage(p);

            // Extract editable text
            String text = "";
            try {
                LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
                text = PdfTextExtractor.getTextFromPage(page, strategy);
            } catch (Throwable ex) {
                try {
                    text = PdfTextExtractor.getTextFromPage(page);
                } catch (Throwable ignored) {}
            }

            SlideContent sc = parseSlideContent(p, text);

            // Check if page visual was rendered via PdfRenderer
            byte[] pageVisual = (renderedSlides != null && (p - 1) < renderedSlides.size())
                    ? renderedSlides.get(p - 1) : null;

            if (pageVisual != null && pageVisual.length > 0) {
                int[] dims = PdfToWordConverter.getImageDimensions(pageVisual, "jpg");
                sc.slideImageBytes = pageVisual;
                sc.imageExtension = "jpg";
                sc.imageWidth = dims[0];
                sc.imageHeight = dims[1];
                sc.isFullPageRender = true;
            } else {
                // Fallback: Check for embedded image XObjects
                List<PdfToWordConverter.ExtractedImage> embedded = extractEmbeddedImages(page);
                if (!embedded.isEmpty()) {
                    PdfToWordConverter.ExtractedImage primary = embedded.get(0);
                    sc.slideImageBytes = primary.data;
                    sc.imageExtension = primary.extension;
                    sc.imageWidth = primary.width;
                    sc.imageHeight = primary.height;
                    sc.isFullPageRender = false;
                }
            }

            slides.add(sc);
        }

        pdfDoc.close();
        reader.close();

        // Build OpenXML PPTX archive
        buildPptxArchive(slides, originalFileName, outputFile);
    }

    private static class SlideContent {
        final int slideNumber;
        final String title;
        final List<String> bulletPoints;
        byte[] slideImageBytes;
        String imageExtension = "jpg";
        int imageWidth = 1920;
        int imageHeight = 1080;
        boolean isFullPageRender = false;

        SlideContent(int slideNumber, String title, List<String> bulletPoints) {
            this.slideNumber = slideNumber;
            this.title = title;
            this.bulletPoints = bulletPoints;
        }
    }

    private static SlideContent parseSlideContent(int slideNum, String rawText) {
        String title = "Slide " + slideNum;
        List<String> bullets = new ArrayList<>();

        if (rawText != null && !rawText.trim().isEmpty()) {
            String[] lines = rawText.split("\r?\n");
            boolean titleFound = false;

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;

                if (!titleFound) {
                    title = cleanTitle(line);
                    titleFound = true;
                } else {
                    bullets.add(cleanBullet(line));
                }
            }
        }

        if (bullets.isEmpty()) {
            bullets.add("(Slide converted from PDF page " + slideNum + ")");
        }

        return new SlideContent(slideNum, title, bullets);
    }

    private static String cleanTitle(String line) {
        if (line.length() > 80) {
            return line.substring(0, 77) + "...";
        }
        return line;
    }

    private static String cleanBullet(String line) {
        String t = line.trim();
        if (t.startsWith("•") || t.startsWith("▪") || t.startsWith("○")) {
            return t.substring(1).trim();
        }
        if (t.startsWith("- ") || t.startsWith("* ")) {
            return t.substring(2).trim();
        }
        return t;
    }

    /**
     * Extracts embedded image XObjects from a PDF page dictionary.
     */
    private static List<PdfToWordConverter.ExtractedImage> extractEmbeddedImages(PdfPage page) {
        List<PdfToWordConverter.ExtractedImage> result = new ArrayList<>();
        try {
            PdfDictionary pageDict = page.getPdfObject();
            PdfDictionary resources = pageDict.getAsDictionary(PdfName.Resources);
            extractImagesFromResources(resources, result);
        } catch (Throwable t) {
            Log.w(TAG, "Failed extracting embedded images: " + t.getMessage());
        }
        return result;
    }

    private static void extractImagesFromResources(PdfDictionary resources, List<PdfToWordConverter.ExtractedImage> outImages) {
        if (resources == null) return;
        PdfDictionary xObjects = resources.getAsDictionary(PdfName.XObject);
        if (xObjects == null) return;

        for (PdfName name : xObjects.keySet()) {
            PdfStream stream = xObjects.getAsStream(name);
            if (stream == null) continue;

            PdfName subtype = stream.getAsName(PdfName.Subtype);
            if (PdfName.Image.equals(subtype)) {
                try {
                    PdfImageXObject imgObj = new PdfImageXObject(stream);
                    byte[] bytes = imgObj.getImageBytes();
                    if (bytes != null && bytes.length > 0) {
                        String ext = imgObj.identifyImageFileExtension();
                        if (ext == null || ext.isEmpty()) {
                            ext = "jpg";
                        } else {
                            ext = ext.toLowerCase(Locale.US);
                            if ("jpeg".equals(ext)) ext = "jpg";
                        }
                        int w = (int) imgObj.getWidth();
                        int h = (int) imgObj.getHeight();
                        if (w <= 0 || h <= 0) {
                            int[] dims = PdfToWordConverter.getImageDimensions(bytes, ext);
                            w = dims[0];
                            h = dims[1];
                        }
                        outImages.add(new PdfToWordConverter.ExtractedImage(bytes, ext, w, h));
                    }
                } catch (Throwable ignored) {}
            } else if (PdfName.Form.equals(subtype)) {
                PdfDictionary subResources = stream.getAsDictionary(PdfName.Resources);
                extractImagesFromResources(subResources, outImages);
            }
        }
    }

    /**
     * Renders PDF pages using Android PdfRenderer into JPEG bytes.
     */
    private static List<byte[]> renderPdfSlidesWithRenderer(Context context, byte[] pdfBytes, int pageCount) {
        List<byte[]> slideImages = new ArrayList<>();
        File tempPdf = null;
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;

        try {
            File cacheDir = context != null ? context.getCacheDir() : null;
            tempPdf = File.createTempFile("pressgo_pptx_render_", ".pdf", cacheDir);
            try (FileOutputStream fos = new FileOutputStream(tempPdf)) {
                fos.write(pdfBytes);
                fos.flush();
            }

            pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            int count = Math.min(pageCount, renderer.getPageCount());

            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = null;
                Bitmap bitmap = null;
                try {
                    page = renderer.openPage(i);
                    int pw = page.getWidth();
                    int ph = page.getHeight();

                    // Crisp 1.5x resolution, max 1920 width
                    float scale = 1.5f;
                    if (pw * scale > 1920f) {
                        scale = 1920f / pw;
                    }
                    int targetW = Math.max(1, Math.round(pw * scale));
                    int targetH = Math.max(1, Math.round(ph * scale));

                    bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                    slideImages.add(baos.toByteArray());
                } catch (Throwable t) {
                    slideImages.add(null);
                } finally {
                    if (bitmap != null) bitmap.recycle();
                    if (page != null) page.close();
                }
            }
        } catch (Throwable t) {
            // JVM unit tests or environment without PdfRenderer
            return null;
        } finally {
            if (renderer != null) {
                try { renderer.close(); } catch (Throwable ignored) {}
            }
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
        return slideImages;
    }

    private static void buildPptxArchive(List<SlideContent> slides, String originalFileName, File outputFile) throws Exception {
        int slideCount = slides.size();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // 1. [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            StringBuilder ctSb = new StringBuilder();
            ctSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n")
                .append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n")
                .append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");

            Set<String> imageExts = new HashSet<>();
            imageExts.add("jpg");
            imageExts.add("jpeg");
            imageExts.add("png");
            imageExts.add("gif");
            for (SlideContent sc : slides) {
                if (sc.slideImageBytes != null && sc.imageExtension != null) {
                    imageExts.add(sc.imageExtension.toLowerCase(Locale.US));
                }
            }
            for (String ext : imageExts) {
                String mime = "png".equals(ext) ? "image/png" : "gif".equals(ext) ? "image/gif" : "image/jpeg";
                ctSb.append("  <Default Extension=\"").append(ext).append("\" ContentType=\"").append(mime).append("\"/>\n");
            }

            ctSb.append("  <Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>\n")
                .append("  <Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>\n")
                .append("  <Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>\n")
                .append("  <Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>\n");

            for (int i = 1; i <= slideCount; i++) {
                ctSb.append("  <Override PartName=\"/ppt/slides/slide").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n");
            }
            ctSb.append("</Types>");
            zos.write(ctSb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>\n" +
                    "</Relationships>";
            zos.write(rootRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. ppt/theme/theme1.xml
            zos.putNextEntry(new ZipEntry("ppt/theme/theme1.xml"));
            String themeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">\n" +
                    "  <a:themeElements>\n" +
                    "    <a:clrScheme name=\"Office\">\n" +
                    "      <a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>\n" +
                    "      <a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>\n" +
                    "      <a:dk2><a:srgbClr val=\"1F497D\"/></a:dk2>\n" +
                    "      <a:lt2><a:srgbClr val=\"EEECE1\"/></a:lt2>\n" +
                    "      <a:accent1><a:srgbClr val=\"4F81BD\"/></a:accent1>\n" +
                    "      <a:accent2><a:srgbClr val=\"C0504D\"/></a:accent2>\n" +
                    "      <a:accent3><a:srgbClr val=\"9BBB59\"/></a:accent3>\n" +
                    "      <a:accent4><a:srgbClr val=\"8064A2\"/></a:accent4>\n" +
                    "      <a:accent5><a:srgbClr val=\"4BACC6\"/></a:accent5>\n" +
                    "      <a:accent6><a:srgbClr val=\"F79646\"/></a:accent6>\n" +
                    "      <a:hlink><a:srgbClr val=\"0000FF\"/></a:hlink>\n" +
                    "      <a:folHlink><a:srgbClr val=\"800080\"/></a:folHlink>\n" +
                    "    </a:clrScheme>\n" +
                    "    <a:fontScheme name=\"Office\">\n" +
                    "      <a:majorFont><a:latin typeface=\"Calibri\"/></a:majorFont>\n" +
                    "      <a:minorFont><a:latin typeface=\"Calibri\"/></a:minorFont>\n" +
                    "    </a:fontScheme>\n" +
                    "    <a:fmtScheme name=\"Office\">\n" +
                    "      <a:fillStyleLst><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:solidFill><a:srgbClr val=\"F2F2F2\"/></a:solidFill><a:solidFill><a:srgbClr val=\"E5E5E5\"/></a:solidFill></a:fillStyleLst>\n" +
                    "      <a:lnStyleLst><a:ln w=\"9525\"><a:solidFill><a:srgbClr val=\"D9D9D9\"/></a:solidFill></a:ln><a:ln w=\"19050\"><a:solidFill><a:srgbClr val=\"D9D9D9\"/></a:solidFill></a:ln><a:ln w=\"25400\"><a:solidFill><a:srgbClr val=\"D9D9D9\"/></a:solidFill></a:ln></a:lnStyleLst>\n" +
                    "      <a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>\n" +
                    "      <a:bgFillStyleLst><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:solidFill><a:srgbClr val=\"FAFAFA\"/></a:solidFill><a:solidFill><a:srgbClr val=\"F5F5F5\"/></a:solidFill></a:bgFillStyleLst>\n" +
                    "    </a:fmtScheme>\n" +
                    "  </a:themeElements>\n" +
                    "  <a:objectDefaults/>\n" +
                    "  <a:extraClrSchemeLst/>\n" +
                    "</a:theme>";
            zos.write(themeXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. ppt/slideMasters/slideMaster1.xml
            zos.putNextEntry(new ZipEntry("ppt/slideMasters/slideMaster1.xml"));
            String slideMasterXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:sldMaster xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <p:cSld>\n" +
                    "    <p:spTree>\n" +
                    "      <p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\n" +
                    "      <p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>\n" +
                    "    </p:spTree>\n" +
                    "  </p:cSld>\n" +
                    "  <p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>\n" +
                    "  <p:sldLayoutIdLst>\n" +
                    "    <p:sldLayoutId id=\"2147483649\" r:id=\"rIdLayout1\"/>\n" +
                    "  </p:sldLayoutIdLst>\n" +
                    "  <p:txStyles>\n" +
                    "    <p:titleStyle><a:lvl1pPr><a:defRPr sz=\"4400\"/></a:lvl1pPr></p:titleStyle>\n" +
                    "    <p:bodyStyle><a:lvl1pPr><a:defRPr sz=\"3200\"/></a:lvl1pPr></p:bodyStyle>\n" +
                    "    <p:otherStyle><a:lvl1pPr><a:defRPr/></a:lvl1pPr></p:otherStyle>\n" +
                    "  </p:txStyles>\n" +
                    "</p:sldMaster>";
            zos.write(slideMasterXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 5. ppt/slideMasters/_rels/slideMaster1.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/slideMasters/_rels/slideMaster1.xml.rels"));
            String masterRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rIdLayout1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>\n" +
                    "  <Relationship Id=\"rIdTheme\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>\n" +
                    "</Relationships>";
            zos.write(masterRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 6. ppt/slideLayouts/slideLayout1.xml
            zos.putNextEntry(new ZipEntry("ppt/slideLayouts/slideLayout1.xml"));
            String slideLayoutXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<p:sldLayout xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" type=\"blank\" preserve=\"1\">\n" +
                    "  <p:cSld name=\"Blank\">\n" +
                    "    <p:spTree>\n" +
                    "      <p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>\n" +
                    "      <p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>\n" +
                    "    </p:spTree>\n" +
                    "  </p:cSld>\n" +
                    "  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>\n" +
                    "</p:sldLayout>";
            zos.write(slideLayoutXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 7. ppt/slideLayouts/_rels/slideLayout1.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/slideLayouts/_rels/slideLayout1.xml.rels"));
            String layoutRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rIdMaster\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>\n" +
                    "</Relationships>";
            zos.write(layoutRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 8. ppt/_rels/presentation.xml.rels
            zos.putNextEntry(new ZipEntry("ppt/_rels/presentation.xml.rels"));
            StringBuilder presRelsSb = new StringBuilder();
            presRelsSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                      .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
                      .append("  <Relationship Id=\"rIdMaster1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>\n")
                      .append("  <Relationship Id=\"rIdTheme1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>\n");

            for (int i = 1; i <= slideCount; i++) {
                presRelsSb.append("  <Relationship Id=\"rId").append(i)
                          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                          .append(i).append(".xml\"/>\n");
            }
            presRelsSb.append("</Relationships>");
            zos.write(presRelsSb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 9. ppt/presentation.xml
            zos.putNextEntry(new ZipEntry("ppt/presentation.xml"));
            StringBuilder presXmlSb = new StringBuilder();
            presXmlSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                     .append("<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"\n")
                     .append("                xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"\n")
                     .append("                xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n")
                     .append("  <p:sldMasterIdLst>\n")
                     .append("    <p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster1\"/>\n")
                     .append("  </p:sldMasterIdLst>\n")
                     .append("  <p:sldIdLst>\n");
            for (int i = 1; i <= slideCount; i++) {
                presXmlSb.append("    <p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i).append("\"/>\n");
            }
            presXmlSb.append("  </p:sldIdLst>\n")
                     .append("  <p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/>\n")
                     .append("  <p:notesSz cx=\"6858000\" cy=\"9144000\"/>\n")
                     .append("  <p:defaultTextStyle/>\n")
                     .append("</p:presentation>");
            zos.write(presXmlSb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 10. Slides, Media, and Slide Relationships
            for (int i = 0; i < slideCount; i++) {
                int slideNum = i + 1;
                SlideContent sc = slides.get(i);
                boolean hasImage = sc.slideImageBytes != null && sc.slideImageBytes.length > 0;
                String imageRelTarget = "../media/slide_" + slideNum + "." + sc.imageExtension;

                // Write media file if present: ppt/media/slide_{slideNum}.ext
                if (hasImage) {
                    zos.putNextEntry(new ZipEntry("ppt/media/slide_" + slideNum + "." + sc.imageExtension));
                    zos.write(sc.slideImageBytes);
                    zos.closeEntry();
                }

                // ppt/slides/slide{i}.xml
                zos.putNextEntry(new ZipEntry("ppt/slides/slide" + slideNum + ".xml"));
                String slideXml = generateSlideXml(sc, hasImage);
                zos.write(slideXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // ppt/slides/_rels/slide{i}.xml.rels
                zos.putNextEntry(new ZipEntry("ppt/slides/_rels/slide" + slideNum + ".xml.rels"));
                StringBuilder sRels = new StringBuilder();
                sRels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                     .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
                     .append("  <Relationship Id=\"rIdLayout\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>\n");
                if (hasImage) {
                    sRels.append("  <Relationship Id=\"rIdImage\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"")
                         .append(imageRelTarget).append("\"/>\n");
                }
                sRels.append("</Relationships>");
                zos.write(sRels.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    private static String generateSlideXml(SlideContent sc, boolean hasImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
          .append("<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"\n")
          .append("       xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"\n")
          .append("       xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n")
          .append("  <p:cSld>\n")
          .append("    <p:spTree>\n")
          .append("      <p:nvGrpSpPr>\n")
          .append("        <p:cNvPr id=\"1\" name=\"\"/>\n")
          .append("        <p:cNvGrpSpPr/>\n")
          .append("        <p:nvPr/>\n")
          .append("      </p:nvGrpSpPr>\n")
          .append("      <p:grpSpPr>\n")
          .append("        <a:xfrm>\n")
          .append("          <a:off x=\"0\" y=\"0\"/>\n")
          .append("          <a:ext cx=\"0\" cy=\"0\"/>\n")
          .append("          <a:chOff x=\"0\" y=\"0\"/>\n")
          .append("          <a:chExt cx=\"0\" cy=\"0\"/>\n")
          .append("        </a:xfrm>\n")
          .append("      </p:grpSpPr>\n");

        if (hasImage && sc.isFullPageRender) {
            // Case 1: High-fidelity Full Page Visual from PDF
            // Center image on 16:9 canvas preserving aspect ratio
            double imgRatio = (double) sc.imageWidth / (double) Math.max(1, sc.imageHeight);
            long shapeW, shapeH, offX, offY;

            if (imgRatio >= SLIDE_ASPECT_RATIO) {
                // Image is wider than 16:9 -> fit to width
                shapeW = SLIDE_WIDTH_EMU;
                shapeH = Math.max(1000L, Math.round(SLIDE_WIDTH_EMU / imgRatio));
                offX = 0L;
                offY = Math.max(0L, (SLIDE_HEIGHT_EMU - shapeH) / 2L);
            } else {
                // Image is taller than 16:9 (e.g. Portrait PDF page) -> fit to height
                shapeH = SLIDE_HEIGHT_EMU;
                shapeW = Math.max(1000L, Math.round(SLIDE_HEIGHT_EMU * imgRatio));
                offY = 0L;
                offX = Math.max(0L, (SLIDE_WIDTH_EMU - shapeW) / 2L);
            }

            sb.append("      <p:pic>\n")
              .append("        <p:nvPicPr>\n")
              .append("          <p:cNvPr id=\"2\" name=\"Slide Visual\"/>\n")
              .append("          <p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr>\n")
              .append("          <p:nvPr/>\n")
              .append("        </p:nvPicPr>\n")
              .append("        <p:blipFill>\n")
              .append("          <a:blip r:embed=\"rIdImage\"/>\n")
              .append("          <a:stretch><a:fillRect/></a:stretch>\n")
              .append("        </p:blipFill>\n")
              .append("        <p:spPr>\n")
              .append("          <a:xfrm>\n")
              .append("            <a:off x=\"").append(offX).append("\" y=\"").append(offY).append("\"/>\n")
              .append("            <a:ext cx=\"").append(shapeW).append("\" cy=\"").append(shapeH).append("\"/>\n")
              .append("          </a:xfrm>\n")
              .append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n")
              .append("        </p:spPr>\n")
              .append("      </p:pic>\n");

        } else if (hasImage) {
            // Case 2: Extracted Embedded Image + Text
            // Title Shape at top
            appendTitleShape(sb, sc.title);

            // Left side: Text Bullet Points
            appendTextShape(sb, sc.bulletPoints, 838200L, 1800000L, 5200000L, 4500000L);

            // Right side: Extracted Picture
            long picAreaW = 4800000L;
            long picAreaH = 4300000L;
            double imgRatio = (double) sc.imageWidth / (double) Math.max(1, sc.imageHeight);
            double areaRatio = (double) picAreaW / (double) picAreaH;
            long shapeW, shapeH;
            if (imgRatio >= areaRatio) {
                shapeW = picAreaW;
                shapeH = Math.max(1000L, Math.round(picAreaW / imgRatio));
            } else {
                shapeH = picAreaH;
                shapeW = Math.max(1000L, Math.round(picAreaH * imgRatio));
            }
            long offX = 6500000L + Math.max(0L, (picAreaW - shapeW) / 2L);
            long offY = 1900000L + Math.max(0L, (picAreaH - shapeH) / 2L);

            sb.append("      <p:pic>\n")
              .append("        <p:nvPicPr>\n")
              .append("          <p:cNvPr id=\"4\" name=\"Picture 1\"/>\n")
              .append("          <p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr>\n")
              .append("          <p:nvPr/>\n")
              .append("        </p:nvPicPr>\n")
              .append("        <p:blipFill>\n")
              .append("          <a:blip r:embed=\"rIdImage\"/>\n")
              .append("          <a:stretch><a:fillRect/></a:stretch>\n")
              .append("        </p:blipFill>\n")
              .append("        <p:spPr>\n")
              .append("          <a:xfrm>\n")
              .append("            <a:off x=\"").append(offX).append("\" y=\"").append(offY).append("\"/>\n")
              .append("            <a:ext cx=\"").append(shapeW).append("\" cy=\"").append(shapeH).append("\"/>\n")
              .append("          </a:xfrm>\n")
              .append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n")
              .append("        </p:spPr>\n")
              .append("      </p:pic>\n");

        } else {
            // Case 3: Text Only
            appendTitleShape(sb, sc.title);
            appendTextShape(sb, sc.bulletPoints, 838200L, 1800000L, 10515600L, 4500000L);
        }

        sb.append("    </p:spTree>\n")
          .append("  </p:cSld>\n")
          .append("  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>\n")
          .append("</p:sld>");

        return sb.toString();
    }

    private static void appendTitleShape(StringBuilder sb, String title) {
        sb.append("      <p:sp>\n")
          .append("        <p:nvSpPr>\n")
          .append("          <p:cNvPr id=\"2\" name=\"Title 1\"/>\n")
          .append("          <p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>\n")
          .append("          <p:nvPr><p:ph type=\"title\"/></p:nvPr>\n")
          .append("        </p:nvSpPr>\n")
          .append("        <p:spPr>\n")
          .append("          <a:xfrm>\n")
          .append("            <a:off x=\"838200\" y=\"500000\"/>\n")
          .append("            <a:ext cx=\"10515600\" cy=\"1100000\"/>\n")
          .append("          </a:xfrm>\n")
          .append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n")
          .append("        </p:spPr>\n")
          .append("        <p:txBody>\n")
          .append("          <a:bodyPr/>\n")
          .append("          <a:lstStyle/>\n")
          .append("          <a:p>\n")
          .append("            <a:r>\n")
          .append("              <a:rPr lang=\"en-US\" sz=\"3200\" b=\"1\">\n")
          .append("                <a:solidFill><a:srgbClr val=\"1E293B\"/></a:solidFill>\n")
          .append("              </a:rPr>\n")
          .append("              <a:t>").append(escapeXml(title)).append("</a:t>\n")
          .append("            </a:r>\n")
          .append("          </a:p>\n")
          .append("        </p:txBody>\n")
          .append("      </p:sp>\n");
    }

    private static void appendTextShape(StringBuilder sb, List<String> bullets,
                                        long offX, long offY, long cx, long cy) {
        sb.append("      <p:sp>\n")
          .append("        <p:nvSpPr>\n")
          .append("          <p:cNvPr id=\"3\" name=\"Content 2\"/>\n")
          .append("          <p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>\n")
          .append("          <p:nvPr><p:ph idx=\"1\"/></p:nvPr>\n")
          .append("        </p:nvSpPr>\n")
          .append("        <p:spPr>\n")
          .append("          <a:xfrm>\n")
          .append("            <a:off x=\"").append(offX).append("\" y=\"").append(offY).append("\"/>\n")
          .append("            <a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>\n")
          .append("          </a:xfrm>\n")
          .append("          <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n")
          .append("        </p:spPr>\n")
          .append("        <p:txBody>\n")
          .append("          <a:bodyPr/>\n")
          .append("          <a:lstStyle/>\n");

        for (String bullet : bullets) {
            sb.append("          <a:p>\n")
              .append("            <a:pPr lvl=\"0\" marL=\"288000\" indent=\"-288000\">\n")
              .append("              <a:buChar char=\"&#8226;\"/>\n")
              .append("            </a:pPr>\n")
              .append("            <a:r>\n")
              .append("              <a:rPr lang=\"en-US\" sz=\"1800\">\n")
              .append("                <a:solidFill><a:srgbClr val=\"334155\"/></a:solidFill>\n")
              .append("              </a:rPr>\n")
              .append("              <a:t>").append(escapeXml(bullet)).append("</a:t>\n")
              .append("            </a:r>\n")
              .append("          </a:p>\n");
        }

        sb.append("        </p:txBody>\n")
          .append("      </p:sp>\n");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c >= 0x20 || c == 0x09 || c == 0x0A || c == 0x0D) {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
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
}
