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
 * Production-grade PDF to Word (.docx) Converter for PressGo.
 * 100% Pure Java implementation preserving text, headings, bullet structures,
 * and high-fidelity embedded images and visual graphics into standard OpenXML packages.
 */
public final class PdfToWordConverter {

    private static final String TAG = "PdfToWordConverter";

    // Standard A4 printable area constraints in EMUs (1 inch = 914,400 EMUs)
    // Printable width: approx 6.0 inches (5,486,400 EMUs), height: approx 7.5 inches (6,858,000 EMUs)
    private static final long MAX_IMAGE_WIDTH_EMU = 5486400L;
    private static final long MAX_IMAGE_HEIGHT_EMU = 6858000L;
    private static final long EMU_PER_PIXEL = 9525L; // Standard 96 DPI mapping

    private PdfToWordConverter() {}

    /**
     * Converts a PDF file from a URI into an editable Word document (.docx).
     *
     * @param context    Android Context
     * @param uri        Uri of the source PDF
     * @param outputFile Target .docx file
     * @return Converted Word File
     * @throws Exception On conversion failure
     */
    public static File convertPdfToWord(Context context, Uri uri, File outputFile) throws Exception {
        if (context == null || uri == null || outputFile == null) {
            throw new IllegalArgumentException("Context, Uri, and OutputFile must not be null");
        }

        byte[] pdfBytes = readAllBytes(context, uri);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new Exception("Selected PDF file is empty or could not be read");
        }

        convertPdfBytesToWord(context, pdfBytes, DocumentUtils.getFileName(context, uri), outputFile);
        return outputFile;
    }

    /**
     * Converts raw bytes of a PDF file into an editable Word document (.docx) without Context.
     */
    public static void convertPdfBytesToWord(byte[] pdfBytes, String originalFileName, File outputFile) throws Exception {
        convertPdfBytesToWord(null, pdfBytes, originalFileName, outputFile);
    }

    /**
     * Converts raw bytes of a PDF file into an editable Word document (.docx).
     */
    public static void convertPdfBytesToWord(Context context, byte[] pdfBytes, String originalFileName, File outputFile) throws Exception {
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
        List<byte[]> renderedPageImages = renderPdfPagesWithRenderer(context, pdfBytes, pageCount);

        List<PageContent> pages = new ArrayList<>();
        List<ExtractedImage> allImages = new ArrayList<>();
        int globalImageCounter = 1;

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
            if (text == null) text = "";

            // Extract raw embedded image XObjects
            List<ExtractedImage> pageImages = extractEmbeddedImages(page);

            // If page has no embedded image XObjects AND very little text (e.g. Scanned document, poster, or diagram),
            // render the page visual so graphical content is preserved without duplicate text overlays.
            boolean isScannedOrSparse = text.trim().length() < 40;
            if (pageImages.isEmpty() && isScannedOrSparse && renderedPageImages != null && (p - 1) < renderedPageImages.size()) {
                byte[] renderedBytes = renderedPageImages.get(p - 1);
                if (renderedBytes != null && renderedBytes.length > 0) {
                    int[] dims = getImageDimensions(renderedBytes, "jpg");
                    pageImages.add(new ExtractedImage(renderedBytes, "jpg", dims[0], dims[1]));
                }
            }

            // Assign unique relationship IDs to images
            for (ExtractedImage img : pageImages) {
                img.relationshipId = "rIdImg" + globalImageCounter;
                img.fileName = "image_" + globalImageCounter + "." + img.extension;
                globalImageCounter++;
                allImages.add(img);
            }

            pages.add(new PageContent(p, text, pageImages));
        }

        pdfDoc.close();
        reader.close();

        // Build OpenXML DOCX archive
        buildDocxArchive(pages, allImages, originalFileName, outputFile);
    }

    /**
     * Data holder for an extracted image.
     */
    public static class ExtractedImage {
        public final byte[] data;
        public final String extension;
        public final int width;
        public final int height;
        public String relationshipId;
        public String fileName;

        public ExtractedImage(byte[] data, String extension, int width, int height) {
            this.data = data;
            this.extension = extension != null ? extension : "jpg";
            this.width = width > 0 ? width : 800;
            this.height = height > 0 ? height : 600;
        }
    }

    private static class PageContent {
        final int pageNumber;
        final String text;
        final List<ExtractedImage> images;

        PageContent(int pageNumber, String text, List<ExtractedImage> images) {
            this.pageNumber = pageNumber;
            this.text = text;
            this.images = images != null ? images : new ArrayList<>();
        }
    }

    /**
     * Extracts embedded image XObjects from a PDF page dictionary.
     */
    private static List<ExtractedImage> extractEmbeddedImages(PdfPage page) {
        List<ExtractedImage> result = new ArrayList<>();
        try {
            PdfDictionary pageDict = page.getPdfObject();
            PdfDictionary resources = pageDict.getAsDictionary(PdfName.Resources);
            extractImagesFromResources(resources, result);
        } catch (Throwable t) {
            Log.w(TAG, "Failed extracting embedded images: " + t.getMessage());
        }
        return result;
    }

    private static void extractImagesFromResources(PdfDictionary resources, List<ExtractedImage> outImages) {
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
                            int[] dims = getImageDimensions(bytes, ext);
                            w = dims[0];
                            h = dims[1];
                        }
                        outImages.add(new ExtractedImage(bytes, ext, w, h));
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
     * Wrapped in Throwable catch so unit tests on JVM continue smoothly without crashing.
     */
    private static List<byte[]> renderPdfPagesWithRenderer(Context context, byte[] pdfBytes, int pageCount) {
        List<byte[]> pageImages = new ArrayList<>();
        File tempPdf = null;
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;

        try {
            File cacheDir = context != null ? context.getCacheDir() : null;
            tempPdf = File.createTempFile("pressgo_docx_render_", ".pdf", cacheDir);
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

                    // 1.5x resolution for crisp graphics, clamped to max 1920 width
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, baos);
                    pageImages.add(baos.toByteArray());
                } catch (Throwable t) {
                    pageImages.add(null);
                } finally {
                    if (bitmap != null) bitmap.recycle();
                    if (page != null) page.close();
                }
            }
        } catch (Throwable t) {
            // JVM unit tests or devices without PdfRenderer support
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
        return pageImages;
    }

    /**
     * Fast dimension extractor for JPEG and PNG images in pure Java.
     */
    public static int[] getImageDimensions(byte[] data, String ext) {
        if (data == null || data.length < 10) return new int[]{800, 600};

        // 1. Try Android BitmapFactory first if available
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                return new int[]{opts.outWidth, opts.outHeight};
            }
        } catch (Throwable ignored) {}

        // 2. Pure Java PNG header parser: 89 50 4E 47 0D 0A 1A 0A
        try {
            if (data.length > 24 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
                int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
                if (w > 0 && h > 0) return new int[]{w, h};
            }

            // 3. Pure Java JPEG header parser: FF D8
            if (data.length > 4 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                int offset = 2;
                while (offset < data.length - 8) {
                    if ((data[offset] & 0xFF) != 0xFF) {
                        offset++;
                        continue;
                    }
                    int marker = data[offset + 1] & 0xFF;
                    if (marker >= 0xC0 && marker <= 0xC3) {
                        int h = ((data[offset + 5] & 0xFF) << 8) | (data[offset + 6] & 0xFF);
                        int w = ((data[offset + 7] & 0xFF) << 8) | (data[offset + 8] & 0xFF);
                        if (w > 0 && h > 0) return new int[]{w, h};
                    }
                    int length = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
                    offset += 2 + length;
                }
            }
        } catch (Throwable ignored) {}

        return new int[]{800, 600};
    }

    private static void buildDocxArchive(List<PageContent> pages, List<ExtractedImage> allImages,
                                         String originalFileName, File outputFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // 1. [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            StringBuilder ctSb = new StringBuilder();
            ctSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n")
                .append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n")
                .append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");

            Set<String> addedExts = new HashSet<>();
            for (ExtractedImage img : allImages) {
                String ext = img.extension.toLowerCase(Locale.US);
                if (!addedExts.contains(ext)) {
                    addedExts.add(ext);
                    String mime = "png".equals(ext) ? "image/png" : "gif".equals(ext) ? "image/gif" : "image/jpeg";
                    ctSb.append("  <Default Extension=\"").append(ext).append("\" ContentType=\"").append(mime).append("\"/>\n");
                }
            }
            ctSb.append("  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n")
                .append("</Types>");
            zos.write(ctSb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            String rootRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                    "</Relationships>";
            zos.write(rootRels.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. word/_rels/document.xml.rels
            zos.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            StringBuilder docRelsSb = new StringBuilder();
            docRelsSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                     .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n");
            for (ExtractedImage img : allImages) {
                docRelsSb.append("  <Relationship Id=\"").append(img.relationshipId)
                         .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                         .append(img.fileName).append("\"/>\n");
            }
            docRelsSb.append("</Relationships>");
            zos.write(docRelsSb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. Write image media entries: word/media/image_X.ext
            for (ExtractedImage img : allImages) {
                zos.putNextEntry(new ZipEntry("word/media/" + img.fileName));
                zos.write(img.data);
                zos.closeEntry();
            }

            // 5. word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String docXml = generateDocumentXml(pages, originalFileName);
            zos.write(docXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static String generateDocumentXml(List<PageContent> pages, String originalFileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
          .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"\n")
          .append("            xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"\n")
          .append("            xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"\n")
          .append("            xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"\n")
          .append("            xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
          .append("  <w:body>\n");

        // Optional Document Header Banner
        if (originalFileName != null && !originalFileName.isEmpty()) {
            String cleanName = DocumentUtils.getFileNameWithoutExtension(originalFileName);
            sb.append("    <w:p>\n")
              .append("      <w:pPr>\n")
              .append("        <w:jc w:val=\"right\"/>\n")
              .append("        <w:spacing w:after=\"200\"/>\n")
              .append("      </w:pPr>\n")
              .append("      <w:r>\n")
              .append("        <w:rPr>\n")
              .append("          <w:sz w:val=\"17\"/>\n")
              .append("          <w:color w:val=\"94A3B8\"/>\n")
              .append("        </w:rPr>\n")
              .append("        <w:t xml:space=\"preserve\">").append(escapeXml(cleanName)).append("</w:t>\n")
              .append("      </w:r>\n")
              .append("    </w:p>\n");
        }

        int inlineDocPrId = 100;

        for (int pIdx = 0; pIdx < pages.size(); pIdx++) {
            PageContent pc = pages.get(pIdx);

            // Page break between pages (except before page 1)
            if (pIdx > 0) {
                sb.append("    <w:p>\n")
                  .append("      <w:r><w:br w:type=\"page\"/></w:r>\n")
                  .append("    </w:p>\n");
            }

            String[] lines = pc.text.split("\r?\n");
            boolean pageHasText = false;
            int firstParagraphIndex = 0;

            // Output heading or initial lines
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                pageHasText = true;

                boolean isHeading = isLikelyHeading(line, i == 0);
                if (isHeading) {
                    sb.append("    <w:p>\n")
                      .append("      <w:pPr>\n")
                      .append("        <w:spacing w:before=\"240\" w:after=\"120\"/>\n")
                      .append("      </w:pPr>\n")
                      .append("      <w:r>\n")
                      .append("        <w:rPr>\n")
                      .append("          <w:b/>\n")
                      .append("          <w:sz w:val=\"28\"/>\n")
                      .append("          <w:color w:val=\"1E3A8A\"/>\n")
                      .append("        </w:rPr>\n")
                      .append("        <w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>\n")
                      .append("      </w:r>\n")
                      .append("    </w:p>\n");
                    firstParagraphIndex = i + 1;
                    break;
                }
            }

            // Output Page Images (DrawingML inline pictures)
            for (ExtractedImage img : pc.images) {
                appendDrawingImage(sb, img, inlineDocPrId++);
            }

            // Output Remaining Paragraphs and Bullet Points
            for (int i = firstParagraphIndex; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    sb.append("    <w:p><w:pPr><w:spacing w:after=\"100\"/></w:pPr><w:r><w:t></w:t></w:r></w:p>\n");
                    continue;
                }

                pageHasText = true;
                boolean isHeading = isLikelyHeading(line, false);
                boolean isBullet = isBulletLine(line);

                if (isHeading) {
                    sb.append("    <w:p>\n")
                      .append("      <w:pPr>\n")
                      .append("        <w:spacing w:before=\"240\" w:after=\"120\"/>\n")
                      .append("      </w:pPr>\n")
                      .append("      <w:r>\n")
                      .append("        <w:rPr>\n")
                      .append("          <w:b/>\n")
                      .append("          <w:sz w:val=\"28\"/>\n")
                      .append("          <w:color w:val=\"1E3A8A\"/>\n")
                      .append("        </w:rPr>\n")
                      .append("        <w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>\n")
                      .append("      </w:r>\n")
                      .append("    </w:p>\n");
                } else if (isBullet) {
                    String cleanContent = cleanBulletContent(line);
                    sb.append("    <w:p>\n")
                      .append("      <w:pPr>\n")
                      .append("        <w:ind w:left=\"720\"/>\n")
                      .append("        <w:spacing w:after=\"100\"/>\n")
                      .append("      </w:pPr>\n")
                      .append("      <w:r>\n")
                      .append("        <w:rPr>\n")
                      .append("          <w:b/>\n")
                      .append("          <w:color w:val=\"1E88E5\"/>\n")
                      .append("        </w:rPr>\n")
                      .append("        <w:t>•  </w:t>\n")
                      .append("      </w:r>\n")
                      .append("      <w:r>\n")
                      .append("        <w:rPr>\n")
                      .append("          <w:sz w:val=\"22\"/>\n")
                      .append("          <w:color w:val=\"1E293B\"/>\n")
                      .append("        </w:rPr>\n")
                      .append("        <w:t xml:space=\"preserve\">").append(escapeXml(cleanContent)).append("</w:t>\n")
                      .append("      </w:r>\n")
                      .append("    </w:p>\n");
                } else {
                    sb.append("    <w:p>\n")
                      .append("      <w:pPr>\n")
                      .append("        <w:spacing w:after=\"120\"/>\n")
                      .append("      </w:pPr>\n")
                      .append("      <w:r>\n")
                      .append("        <w:rPr>\n")
                      .append("          <w:sz w:val=\"22\"/>\n")
                      .append("          <w:color w:val=\"1E293B\"/>\n")
                      .append("        </w:rPr>\n")
                      .append("        <w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>\n")
                      .append("      </w:r>\n")
                      .append("    </w:p>\n");
                }
            }

            if (!pageHasText && pc.images.isEmpty()) {
                sb.append("    <w:p>\n")
                  .append("      <w:pPr><w:jc w:val=\"center\"/></w:pPr>\n")
                  .append("      <w:r>\n")
                  .append("        <w:rPr><w:i/><w:color w:val=\"94A3B8\"/></w:rPr>\n")
                  .append("        <w:t xml:space=\"preserve\">(Page ").append(pc.pageNumber).append(")</w:t>\n")
                  .append("      </w:r>\n")
                  .append("    </w:p>\n");
            }
        }

        // Section Properties: A4 Page & Standard Margins (1 inch / 1440 dxa)
        sb.append("    <w:sectPr>\n")
          .append("      <w:pgSz w:w=\"11906\" w:h=\"16838\"/>\n")
          .append("      <w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/>\n")
          .append("    </w:sectPr>\n")
          .append("  </w:body>\n")
          .append("</w:document>");

        return sb.toString();
    }

    /**
     * Appends an OpenXML DrawingML inline picture shape into document.xml.
     */
    private static void appendDrawingImage(StringBuilder sb, ExtractedImage img, int docPrId) {
        long rawW = Math.max(1L, (long) img.width * EMU_PER_PIXEL);
        long rawH = Math.max(1L, (long) img.height * EMU_PER_PIXEL);

        long cx = rawW;
        long cy = rawH;

        // Scale to fit printable page area strictly preserving aspect ratio
        double scaleW = cx > MAX_IMAGE_WIDTH_EMU ? ((double) MAX_IMAGE_WIDTH_EMU / (double) cx) : 1.0;
        double scaleH = cy > MAX_IMAGE_HEIGHT_EMU ? ((double) MAX_IMAGE_HEIGHT_EMU / (double) cy) : 1.0;
        double scale = Math.min(scaleW, scaleH);

        cx = Math.max(1000L, Math.round(cx * scale));
        cy = Math.max(1000L, Math.round(cy * scale));

        sb.append("    <w:p>\n")
          .append("      <w:pPr>\n")
          .append("        <w:jc w:val=\"center\"/>\n")
          .append("        <w:spacing w:before=\"140\" w:after=\"140\"/>\n")
          .append("      </w:pPr>\n")
          .append("      <w:r>\n")
          .append("        <w:drawing>\n")
          .append("          <wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">\n")
          .append("            <wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>\n")
          .append("            <wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>\n")
          .append("            <wp:docPr id=\"").append(docPrId).append("\" name=\"Picture ").append(docPrId).append("\"/>\n")
          .append("            <wp:cNvGraphicFramePr>\n")
          .append("              <a:graphicFrameLocks xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" noChangeAspect=\"1\"/>\n")
          .append("            </wp:cNvGraphicFramePr>\n")
          .append("            <a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">\n")
          .append("              <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
          .append("                <pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
          .append("                  <pic:nvPicPr>\n")
          .append("                    <pic:cNvPr id=\"").append(docPrId).append("\" name=\"").append(img.fileName).append("\"/>\n")
          .append("                    <pic:cNvPicPr/>\n")
          .append("                  </pic:nvPicPr>\n")
          .append("                  <pic:blipFill>\n")
          .append("                    <a:blip r:embed=\"").append(img.relationshipId).append("\"/>\n")
          .append("                    <a:stretch><a:fillRect/></a:stretch>\n")
          .append("                  </pic:blipFill>\n")
          .append("                  <pic:spPr>\n")
          .append("                    <a:xfrm>\n")
          .append("                      <a:off x=\"0\" y=\"0\"/>\n")
          .append("                      <a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>\n")
          .append("                    </a:xfrm>\n")
          .append("                    <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n")
          .append("                  </pic:spPr>\n")
          .append("                </pic:pic>\n")
          .append("              </a:graphicData>\n")
          .append("            </a:graphic>\n")
          .append("          </wp:inline>\n")
          .append("        </w:drawing>\n")
          .append("      </w:r>\n")
          .append("    </w:p>\n");
    }

    private static boolean isLikelyHeading(String line, boolean isFirstLine) {
        if (line == null || line.length() > 90) return false;
        String trimmed = line.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith(",") || trimmed.endsWith(";")) return false;

        if (trimmed.matches("(?i)^(chapter|section|part)\\s+.*")) return true;
        if (trimmed.matches("^\\d+(\\.\\d+)*\\s+[A-Z].*")) return true;

        if (trimmed.length() <= 50) {
            if (trimmed.equals(trimmed.toUpperCase(Locale.US)) && trimmed.length() > 3) return true;
            if (isFirstLine && trimmed.length() > 3) return true;
        }

        return false;
    }

    private static boolean isBulletLine(String line) {
        if (line == null) return false;
        String t = line.trim();
        return t.startsWith("•") || t.startsWith("- ") || t.startsWith("* ") ||
               t.startsWith("▪") || t.startsWith("○") || t.matches("^[0-9]+\\)\\s+.*");
    }

    private static String cleanBulletContent(String line) {
        String t = line.trim();
        if (t.startsWith("•") || t.startsWith("▪") || t.startsWith("○")) {
            return t.substring(1).trim();
        }
        if (t.startsWith("- ") || t.startsWith("* ")) {
            return t.substring(2).trim();
        }
        return t;
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
