package com.pressgo.app.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.pressgo.app.databinding.ActivityConversionOptionsBinding;
import com.pressgo.app.models.ConversionRecord;
import com.pressgo.app.repositories.ConversionRepository;
import com.pressgo.app.utils.DocumentUtils;
import com.pressgo.app.utils.FileDownloadUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ConversionOptionsActivity extends AppCompatActivity {
    private static final String TAG = "ConversionOptions";

    private ActivityConversionOptionsBinding binding;
    private String conversionType;
    private String conversionTitle;
    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private File lastConvertedFile;

    private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedUris.clear();
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            takeUriPermission(uri);
                            selectedUris.add(uri);
                        }
                    } else if (data.getData() != null) {
                        Uri uri = data.getData();
                        takeUriPermission(uri);
                        selectedUris.add(uri);
                    }
                    updateSelectedFilesUI();
                }
            }
    );

    private void takeUriPermission(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityConversionOptionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversionType = getIntent().getStringExtra("type");
        if (conversionType == null || conversionType.isEmpty()) {
            conversionType = "IMAGES_TO_PDF";
        }
        conversionTitle = getIntent().getStringExtra("title");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(conversionTitle != null ? conversionTitle : "Conversion Options");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupHints();
        setupListeners();
    }

    private void setupHints() {
        if ("WORD_TO_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. document.pdf)");
            binding.tvSelectedFiles.setText("No Word document selected (tap Select Files to choose .docx or .doc)");
        } else if ("PPTX_TO_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. presentation.pdf)");
            binding.tvSelectedFiles.setText("No PowerPoint selected (tap Select Files to choose .pptx or .ppt)");
        } else if ("PDF_TO_WORD".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. document.docx)");
            binding.tvSelectedFiles.setText("No PDF selected (tap Select Files to choose 1 PDF)");
        } else if ("PDF_TO_PPTX".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. presentation.pptx)");
            binding.tvSelectedFiles.setText("No PDF selected (tap Select Files to choose 1 PDF)");
        } else if ("MERGE_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. combined.pdf)");
            binding.tvSelectedFiles.setText("No PDFs selected (tap Select Files to choose 2+ PDFs)");
        } else if ("SPLIT_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. split_pages.zip)");
            binding.tvSelectedFiles.setText("No PDF selected (tap Select Files to choose 1 PDF)");
        } else if ("PDF_TO_TXT".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. extracted_text.txt)");
            binding.tvSelectedFiles.setText("No PDF selected (tap Select Files to choose 1 PDF)");
        } else if ("CODE_TO_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. lab_assignment.pdf)");
            binding.tvSelectedFiles.setText("No code files selected (select .py, .java, .cpp, etc.)");
        } else if ("PDF_TO_IMAGES".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. page_images.zip)");
            binding.tvSelectedFiles.setText("No PDF selected (tap Select Files to choose 1 PDF)");
        } else if ("TXT_TO_PDF".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. notes.pdf)");
            binding.tvSelectedFiles.setText("No text files selected (tap Select Files to choose text files)");
        } else if ("IMAGE_FORMAT".equals(conversionType)) {
            binding.etOutputName.setHint("Custom Filename (e.g. converted.png or converted.jpg)");
            binding.tvSelectedFiles.setText("No images selected (tap Select Files to choose JPG, PNG, WEBP)");
        } else {
            binding.etOutputName.setHint("Custom Filename (e.g. converted.pdf)");
            binding.tvSelectedFiles.setText("No files selected (tap Select Files to choose images)");
        }
    }

    private void setupListeners() {
        binding.btnSelectFiles.setOnClickListener(v -> openFilePicker());
        binding.btnStartConversion.setOnClickListener(v -> startConversion());

        binding.btnPreviewResult.setOnClickListener(v -> previewLastConvertedFile());
        binding.btnOpenResult.setOnClickListener(v -> openLastConvertedFile());
        binding.btnDownloadResult.setOnClickListener(v -> downloadLastConvertedFile());
        binding.btnShareResult.setOnClickListener(v -> shareLastConvertedFile());
        binding.btnSendToShopkeeper.setOnClickListener(v -> sendFileToShopkeeper());
        binding.btnViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConversionHistoryActivity.class);
            intent.putExtra(ConversionHistoryActivity.EXTRA_INITIAL_TAB, ConversionHistoryActivity.TAB_CONVERTED);
            startActivity(intent);
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        boolean allowMultiple;
        if ("WORD_TO_PDF".equals(conversionType)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                    "application/rtf",
                    "text/plain",
                    "*/*"
            });
            allowMultiple = false;
        } else if ("PPTX_TO_PDF".equals(conversionType)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.ms-powerpoint",
                    "*/*"
            });
            allowMultiple = false;
        } else if ("IMAGES_TO_PDF".equals(conversionType) || "IMAGE_FORMAT".equals(conversionType)) {
            intent.setType("image/*");
            allowMultiple = true;
        } else if ("PDF_TO_IMAGES".equals(conversionType) || "SPLIT_PDF".equals(conversionType)
                || "PDF_TO_TXT".equals(conversionType) || "PDF_TO_WORD".equals(conversionType)
                || "PDF_TO_PPTX".equals(conversionType)) {
            intent.setType("application/pdf");
            allowMultiple = false;
        } else if ("MERGE_PDF".equals(conversionType)) {
            intent.setType("application/pdf");
            allowMultiple = true;
        } else if ("TXT_TO_PDF".equals(conversionType)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/*", "application/octet-stream"});
            allowMultiple = true;
        } else if ("CODE_TO_PDF".equals(conversionType)) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "text/*", "application/javascript", "application/json", "application/xml", "application/octet-stream", "*/*"
            });
            allowMultiple = true;
        } else {
            intent.setType("*/*");
            allowMultiple = true;
        }

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);

        try {
            pickerLauncher.launch(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType(intent.getType());
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            pickerLauncher.launch(Intent.createChooser(fallback, "Select Files to Convert"));
        }
    }

    private void updateSelectedFilesUI() {
        int count = selectedUris.size();
        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            for (Uri uri : selectedUris) {
                sb.append("• ").append(DocumentUtils.getFileName(this, uri)).append("\n");
            }
            binding.tvSelectedFiles.setText(sb.toString().trim());
            binding.btnStartConversion.setEnabled(true);
        } else {
            setupHints();
            binding.btnStartConversion.setEnabled(false);
        }
    }

    private byte[] readBytesFromUri(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) throw new Exception("Could not open file stream");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos.toByteArray();
        }
    }

    private void startConversion() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Please select file(s) first", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("MERGE_PDF".equals(conversionType) && selectedUris.size() < 2) {
            Toast.makeText(this, "Please select at least 2 PDF files to merge", Toast.LENGTH_LONG).show();
            return;
        }

        String rawOutputName = binding.etOutputName.getText() != null
                ? binding.etOutputName.getText().toString().trim()
                : "";

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "guest_user";

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnStartConversion.setEnabled(false);
        binding.cardResult.setVisibility(View.GONE);

        executor.execute(() -> {
            ConversionRecord record = new ConversionRecord();
            record.setUserId(uid);
            record.setInputFileType(conversionType);
            record.setStatus(ConversionRecord.STATUS_IN_PROGRESS);

            try {
                File tempDir = DocumentUtils.getTempDir(this);
                String inputName = selectedUris.size() == 1
                        ? DocumentUtils.getFileName(this, selectedUris.get(0))
                        : selectedUris.size() + " files (" + DocumentUtils.getFileName(this, selectedUris.get(0)) + ", ...)";
                record.setInputFileName(inputName);

                long totalInputBytes = 0;
                for (Uri u : selectedUris) {
                    totalInputBytes += DocumentUtils.getFileSize(this, u);
                }
                record.setInputSizeBytes(totalInputBytes);

                String customName = !rawOutputName.isEmpty() ? rawOutputName : null;
                File outputFile = null;

                if ("WORD_TO_PDF".equals(conversionType)) {
                    String originalName = DocumentUtils.getFileName(this, selectedUris.get(0));
                    String baseName = DocumentUtils.getFileNameWithoutExtension(originalName);
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : (baseName.isEmpty() ? "word_document" : baseName) + ".pdf";
                    outputFile = new File(tempDir, outName);
                    com.pressgo.app.converters.WordToPdfConverter.convertWordToPdf(this, selectedUris.get(0), outputFile);
                    record.setOutputFileType("PDF");

                } else if ("PPTX_TO_PDF".equals(conversionType)) {
                    String originalName = DocumentUtils.getFileName(this, selectedUris.get(0));
                    String baseName = DocumentUtils.getFileNameWithoutExtension(originalName);
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : (baseName.isEmpty() ? "presentation" : baseName) + ".pdf";
                    outputFile = new File(tempDir, outName);
                    com.pressgo.app.converters.PptxToPdfConverter.convertPptxToPdf(this, selectedUris.get(0), outputFile);
                    record.setOutputFileType("PDF");

                } else if ("PDF_TO_WORD".equals(conversionType)) {
                    String originalName = DocumentUtils.getFileName(this, selectedUris.get(0));
                    String baseName = DocumentUtils.getFileNameWithoutExtension(originalName);
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".docx") ? customName : customName + ".docx")
                            : (baseName.isEmpty() ? "document" : baseName) + ".docx";
                    outputFile = new File(tempDir, outName);
                    com.pressgo.app.converters.PdfToWordConverter.convertPdfToWord(this, selectedUris.get(0), outputFile);
                    record.setOutputFileType("DOCX");

                } else if ("PDF_TO_PPTX".equals(conversionType)) {
                    String originalName = DocumentUtils.getFileName(this, selectedUris.get(0));
                    String baseName = DocumentUtils.getFileNameWithoutExtension(originalName);
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pptx") ? customName : customName + ".pptx")
                            : (baseName.isEmpty() ? "presentation" : baseName) + ".pptx";
                    outputFile = new File(tempDir, outName);
                    com.pressgo.app.converters.PdfToPptxConverter.convertPdfToPptx(this, selectedUris.get(0), outputFile);
                    record.setOutputFileType("PPTX");

                } else if ("IMAGES_TO_PDF".equals(conversionType)) {
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : "images_" + System.currentTimeMillis() + ".pdf";
                    outputFile = new File(tempDir, outName);

                    PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
                    PdfDocument pdfDoc = new PdfDocument(writer);
                    Document doc = new Document(pdfDoc);

                    float printableWidth = pdfDoc.getDefaultPageSize().getWidth() - doc.getLeftMargin() - doc.getRightMargin();
                    float printableHeight = pdfDoc.getDefaultPageSize().getHeight() - doc.getTopMargin() - doc.getBottomMargin();

                    int imagesAdded = 0;
                    for (Uri uri : selectedUris) {
                        byte[] bytes = readBytesFromUri(uri);
                        if (bytes == null || bytes.length == 0) continue;

                        ImageData imgData = null;
                        try {
                            imgData = ImageDataFactory.create(bytes);
                        } catch (Exception e) {
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) {
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                bmp.compress(Bitmap.CompressFormat.JPEG, 92, stream);
                                imgData = ImageDataFactory.create(stream.toByteArray());
                            }
                        }

                        if (imgData != null) {
                            if (imagesAdded > 0) {
                                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                            }
                            Image img = new Image(imgData);
                            img.scaleToFit(printableWidth, printableHeight);
                            img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                            doc.add(img);
                            imagesAdded++;
                        }
                    }

                    if (imagesAdded == 0) {
                        doc.add(new Paragraph("No valid image content found."));
                    }
                    doc.close();
                    record.setOutputFileType("PDF");

                } else if ("MERGE_PDF".equals(conversionType)) {
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : "merged_" + System.currentTimeMillis() + ".pdf";
                    outputFile = new File(tempDir, outName);

                    PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
                    PdfDocument destPdf = new PdfDocument(writer);

                    int mergedDocs = 0;
                    for (Uri uri : selectedUris) {
                        byte[] bytes = readBytesFromUri(uri);
                        if (bytes == null || bytes.length == 0) continue;

                        try {
                            PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
                            PdfDocument srcPdf = new PdfDocument(reader);
                            int srcPages = srcPdf.getNumberOfPages();
                            if (srcPages > 0) {
                                srcPdf.copyPagesTo(1, srcPages, destPdf);
                                mergedDocs++;
                            }
                            srcPdf.close();
                        } catch (Exception ex) {
                            Log.e(TAG, "Error merging PDF: " + DocumentUtils.getFileName(this, uri), ex);
                        }
                    }

                    if (mergedDocs == 0) {
                        destPdf.close();
                        throw new Exception("No valid PDF pages could be merged from the selected files");
                    }
                    destPdf.close();
                    record.setOutputFileType("PDF");

                } else if ("SPLIT_PDF".equals(conversionType)) {
                    Uri pdfUri = selectedUris.get(0);
                    byte[] bytes = readBytesFromUri(pdfUri);
                    if (bytes == null || bytes.length == 0) {
                        throw new Exception("Failed to read selected PDF file");
                    }

                    PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
                    PdfDocument srcPdf = new PdfDocument(reader);
                    int pageCount = srcPdf.getNumberOfPages();
                    if (pageCount <= 0) {
                        srcPdf.close();
                        throw new Exception("PDF document has 0 pages");
                    }

                    if (pageCount == 1) {
                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                                : "split_page1_" + System.currentTimeMillis() + ".pdf";
                        outputFile = new File(tempDir, outName);
                        PdfDocument singleDoc = new PdfDocument(new PdfWriter(new FileOutputStream(outputFile)));
                        srcPdf.copyPagesTo(1, 1, singleDoc);
                        singleDoc.close();
                        record.setOutputFileType("PDF");
                    } else {
                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith(".zip") ? customName : customName + ".zip")
                                : "split_pages_" + System.currentTimeMillis() + ".zip";
                        outputFile = new File(tempDir, outName);

                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                            for (int p = 1; p <= pageCount; p++) {
                                ByteArrayOutputStream singleBaos = new ByteArrayOutputStream();
                                PdfDocument singleDoc = new PdfDocument(new PdfWriter(singleBaos));
                                srcPdf.copyPagesTo(p, p, singleDoc);
                                singleDoc.close();

                                byte[] singleBytes = singleBaos.toByteArray();
                                zos.putNextEntry(new ZipEntry(String.format(Locale.US, "page_%02d.pdf", p)));
                                zos.write(singleBytes, 0, singleBytes.length);
                                zos.closeEntry();
                            }
                        }
                        record.setOutputFileType("ZIP");
                    }
                    srcPdf.close();

                } else if ("PDF_TO_TXT".equals(conversionType)) {
                    Uri pdfUri = selectedUris.get(0);
                    byte[] bytes = readBytesFromUri(pdfUri);
                    if (bytes == null || bytes.length == 0) {
                        throw new Exception("Failed to read selected PDF file");
                    }

                    PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
                    PdfDocument srcPdf = new PdfDocument(reader);
                    int pageCount = srcPdf.getNumberOfPages();

                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".txt") ? customName : customName + ".txt")
                            : "extracted_text_" + System.currentTimeMillis() + ".txt";
                    outputFile = new File(tempDir, outName);

                    StringBuilder sb = new StringBuilder();
                    sb.append("=== EXTRACTED TEXT FROM: ").append(DocumentUtils.getFileName(this, pdfUri)).append(" ===\n\n");

                    for (int p = 1; p <= pageCount; p++) {
                        sb.append("--- Page ").append(p).append(" of ").append(pageCount).append(" ---\n");
                        try {
                            String pageText = PdfTextExtractor.getTextFromPage(srcPdf.getPage(p));
                            if (pageText != null && !pageText.trim().isEmpty()) {
                                sb.append(pageText).append("\n\n");
                            } else {
                                sb.append("(No selectable text on this page or scanned image)\n\n");
                            }
                        } catch (Exception ex) {
                            sb.append("[Error reading page ").append(p).append(": ").append(ex.getMessage()).append("]\n\n");
                        }
                    }
                    srcPdf.close();

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    record.setOutputFileType("TXT");

                } else if ("CODE_TO_PDF".equals(conversionType)) {
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : "code_" + System.currentTimeMillis() + ".pdf";
                    outputFile = new File(tempDir, outName);

                    PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
                    PdfDocument pdfDoc = new PdfDocument(writer);
                    Document doc = new Document(pdfDoc);
                    doc.setMargins(36f, 36f, 36f, 36f);

                    PdfFont codeFont;
                    PdfFont boldFont;
                    try {
                        codeFont = PdfFontFactory.createFont(StandardFonts.COURIER);
                        boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                    } catch (Exception ex) {
                        codeFont = PdfFontFactory.createFont();
                        boldFont = PdfFontFactory.createFont();
                    }

                    int filesProcessed = 0;
                    for (Uri uri : selectedUris) {
                        byte[] bytes = readBytesFromUri(uri);
                        if (bytes == null || bytes.length == 0) continue;

                        if (filesProcessed > 0) {
                            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        }

                        String fileName = DocumentUtils.getFileName(this, uri);
                        Paragraph header = new Paragraph("SOURCE: " + fileName)
                                .setFont(boldFont)
                                .setFontSize(11f)
                                .setBackgroundColor(new DeviceRgb(238, 242, 255))
                                .setPadding(6f);
                        doc.add(header);

                        String codeText = new String(bytes, StandardCharsets.UTF_8);
                        String[] lines = codeText.split("\r?\n");
                        int lineNum = 1;
                        for (String line : lines) {
                            String numberedLine = String.format(Locale.US, "%03d | %s", lineNum, line);
                            Paragraph p = new Paragraph(numberedLine)
                                    .setFont(codeFont)
                                    .setFontSize(8.5f)
                                    .setMultipliedLeading(1.15f)
                                    .setMargin(0);
                            doc.add(p);
                            lineNum++;
                        }
                        filesProcessed++;
                    }

                    if (filesProcessed == 0) {
                        doc.add(new Paragraph("(No code content found)").setFont(boldFont));
                    }
                    doc.close();
                    record.setOutputFileType("PDF");

                } else if ("TXT_TO_PDF".equals(conversionType)) {
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".pdf") ? customName : customName + ".pdf")
                            : "document_" + System.currentTimeMillis() + ".pdf";
                    outputFile = new File(tempDir, outName);

                    PdfWriter writer = new PdfWriter(new FileOutputStream(outputFile));
                    PdfDocument pdfDoc = new PdfDocument(writer);
                    Document doc = new Document(pdfDoc);

                    int filesAdded = 0;
                    for (Uri uri : selectedUris) {
                        byte[] bytes = readBytesFromUri(uri);
                        if (bytes == null || bytes.length == 0) continue;

                        if (filesAdded > 0) {
                            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        }

                        if (selectedUris.size() > 1) {
                            String name = DocumentUtils.getFileName(this, uri);
                            doc.add(new Paragraph("Document: " + name).setBold());
                        }

                        String text = new String(bytes, StandardCharsets.UTF_8);
                        String[] lines = text.split("\r?\n");
                        for (String line : lines) {
                            doc.add(new Paragraph(line.isEmpty() ? " " : line));
                        }
                        filesAdded++;
                    }

                    if (filesAdded == 0) {
                        doc.add(new Paragraph("(Empty text document)"));
                    }
                    doc.close();
                    record.setOutputFileType("PDF");

                } else if ("PDF_TO_IMAGES".equals(conversionType)) {
                    File tempSourcePdf = new File(tempDir, "temp_src_" + System.currentTimeMillis() + ".pdf");
                    try (FileOutputStream fos = new FileOutputStream(tempSourcePdf)) {
                        fos.write(readBytesFromUri(selectedUris.get(0)));
                    }

                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempSourcePdf, ParcelFileDescriptor.MODE_READ_ONLY);
                    if (pfd == null) {
                        throw new Exception("Could not open PDF file descriptor");
                    }

                    PdfRenderer renderer = new PdfRenderer(pfd);
                    int pageCount = renderer.getPageCount();
                    if (pageCount <= 0) {
                        renderer.close();
                        pfd.close();
                        tempSourcePdf.delete();
                        throw new Exception("PDF document has 0 pages");
                    }

                    if (pageCount == 1) {
                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith(".png") ? customName : customName + ".png")
                                : "page1_" + System.currentTimeMillis() + ".png";
                        outputFile = new File(tempDir, outName);

                        PdfRenderer.Page page = renderer.openPage(0);
                        int width = page.getWidth() * 2;
                        int height = page.getHeight() * 2;
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        page.close();

                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        }
                        record.setOutputFileType("PNG");
                    } else {
                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith(".zip") ? customName : customName + ".zip")
                                : "pdf_pages_" + System.currentTimeMillis() + ".zip";
                        outputFile = new File(tempDir, outName);

                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                            for (int i = 0; i < pageCount; i++) {
                                PdfRenderer.Page page = renderer.openPage(i);
                                int width = page.getWidth() * 2;
                                int height = page.getHeight() * 2;
                                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(bitmap);
                                canvas.drawColor(Color.WHITE);
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                                page.close();

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 95, baos);
                                byte[] pageBytes = baos.toByteArray();

                                zos.putNextEntry(new ZipEntry(String.format(Locale.US, "page_%02d.png", (i + 1))));
                                zos.write(pageBytes, 0, pageBytes.length);
                                zos.closeEntry();
                            }
                        }
                        record.setOutputFileType("ZIP");
                    }

                    renderer.close();
                    pfd.close();
                    tempSourcePdf.delete();

                } else if ("IMAGE_FORMAT".equals(conversionType)) {
                    if (selectedUris.size() == 1) {
                        Uri uri = selectedUris.get(0);
                        byte[] bytes = readBytesFromUri(uri);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp == null) {
                            throw new Exception("Could not decode image format");
                        }

                        String inputNameOriginal = DocumentUtils.getFileName(this, uri);
                        String inputExt = DocumentUtils.getFileExtension(inputNameOriginal).toLowerCase(Locale.US);

                        String outExt = "png";
                        if (inputExt.equals("png")) {
                            outExt = "jpg";
                        } else if (inputExt.equals("webp")) {
                            outExt = "png";
                        }

                        if (customName != null) {
                            if (customName.toLowerCase(Locale.US).endsWith(".png")) outExt = "png";
                            if (customName.toLowerCase(Locale.US).endsWith(".jpg") || customName.toLowerCase(Locale.US).endsWith(".jpeg")) outExt = "jpg";
                            if (customName.toLowerCase(Locale.US).endsWith(".webp")) outExt = "webp";
                        }

                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith("." + outExt) ? customName : customName + "." + outExt)
                                : "converted_" + System.currentTimeMillis() + "." + outExt;

                        outputFile = new File(tempDir, outName);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            if ("png".equalsIgnoreCase(outExt)) {
                                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            } else if ("webp".equalsIgnoreCase(outExt)) {
                                bmp.compress(Bitmap.CompressFormat.WEBP, 92, fos);
                            } else {
                                bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                            }
                        }
                        record.setOutputFileType(outExt.toUpperCase(Locale.US));
                    } else {
                        String outName = (customName != null && !customName.isEmpty())
                                ? (customName.toLowerCase(Locale.US).endsWith(".zip") ? customName : customName + ".zip")
                                : "converted_images_" + System.currentTimeMillis() + ".zip";
                        outputFile = new File(tempDir, outName);

                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                            int count = 1;
                            Set<String> usedNames = new HashSet<>();
                            for (Uri uri : selectedUris) {
                                byte[] bytes = readBytesFromUri(uri);
                                if (bytes == null || bytes.length == 0) continue;
                                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                if (bmp == null) continue;

                                String origName = DocumentUtils.getFileName(this, uri);
                                String baseName = DocumentUtils.getFileNameWithoutExtension(origName);
                                if (baseName.isEmpty()) baseName = "image_" + count;

                                String inputExt = DocumentUtils.getFileExtension(origName).toLowerCase(Locale.US);
                                String outExt = inputExt.equals("png") ? "jpg" : "png";

                                String entryName = baseName + "." + outExt;
                                int c = 1;
                                while (usedNames.contains(entryName)) {
                                    entryName = baseName + "_" + c + "." + outExt;
                                    c++;
                                }
                                usedNames.add(entryName);

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                if ("png".equalsIgnoreCase(outExt)) {
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                } else {
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos);
                                }
                                byte[] convertedBytes = baos.toByteArray();

                                zos.putNextEntry(new ZipEntry(entryName));
                                zos.write(convertedBytes, 0, convertedBytes.length);
                                zos.closeEntry();
                                count++;
                            }
                        }
                        record.setOutputFileType("ZIP");
                    }
                } else {
                    // Fallback / legacy zip
                    String outName = (customName != null && !customName.isEmpty())
                            ? (customName.toLowerCase(Locale.US).endsWith(".zip") ? customName : customName + ".zip")
                            : "archive_" + System.currentTimeMillis() + ".zip";
                    outputFile = new File(tempDir, outName);
                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                        Set<String> usedNames = new HashSet<>();
                        for (Uri uri : selectedUris) {
                            String name = DocumentUtils.getFileName(this, uri);
                            if (name.isEmpty()) name = "file_" + System.currentTimeMillis();
                            String finalName = name;
                            int counter = 1;
                            while (usedNames.contains(finalName)) {
                                String base = DocumentUtils.getFileNameWithoutExtension(name);
                                String ext = DocumentUtils.getFileExtension(name);
                                finalName = base + "_" + counter + (ext.isEmpty() ? "" : "." + ext);
                                counter++;
                            }
                            usedNames.add(finalName);

                            zos.putNextEntry(new ZipEntry(finalName));
                            byte[] bytes = readBytesFromUri(uri);
                            zos.write(bytes, 0, bytes.length);
                            zos.closeEntry();
                        }
                    }
                    record.setOutputFileType("ZIP");
                }

                if (outputFile != null && outputFile.exists() && outputFile.length() > 0) {
                    record.setOutputFileName(outputFile.getName());
                    record.setOutputSizeBytes(outputFile.length());
                    record.setLocalOutputPath(outputFile.getAbsolutePath());
                    record.setStatus(ConversionRecord.STATUS_SUCCESS);

                    final File finalFile = outputFile;
                    runOnUiThread(() -> {
                        lastConvertedFile = finalFile;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnStartConversion.setEnabled(true);
                        binding.cardResult.setVisibility(View.VISIBLE);
                        binding.tvResultTitle.setText("Conversion Complete!");
                        binding.tvResultDetails.setText(
                                "Output: " + finalFile.getName() +
                                "\nSize: " + DocumentUtils.formatFileSize(finalFile.length()) +
                                "\nStatus: Ready to Download, Share, or Send for Printing"
                        );
                        Toast.makeText(ConversionOptionsActivity.this, "Converted: " + finalFile.getName(), Toast.LENGTH_SHORT).show();
                    });
                } else {
                    throw new Exception("Output file was not created or is empty");
                }

            } catch (Exception e) {
                Log.e(TAG, "Conversion error", e);
                record.setStatus(ConversionRecord.STATUS_FAILED);
                record.setErrorMessage(e.getMessage());
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnStartConversion.setEnabled(true);
                    Toast.makeText(ConversionOptionsActivity.this, "Conversion failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                });
            } finally {
                try {
                    ConversionRepository.getInstance().saveConversionRecord(record, task -> {});
                } catch (Exception ignored) {}
            }
        });
    }

    private void previewLastConvertedFile() {
        if (lastConvertedFile == null || !lastConvertedFile.exists()) {
            Toast.makeText(this, "No converted file available to preview", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.openInAppPreview(this, lastConvertedFile);
    }

    private void openLastConvertedFile() {
        if (lastConvertedFile == null || !lastConvertedFile.exists()) {
            Toast.makeText(this, "No converted file available to open", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.openFile(this, lastConvertedFile);
    }

    private void downloadLastConvertedFile() {
        if (lastConvertedFile == null || !lastConvertedFile.exists()) {
            Toast.makeText(this, "No converted file available", Toast.LENGTH_SHORT).show();
            return;
        }

        FileDownloadUtils.saveFileToDownloads(this, lastConvertedFile, lastConvertedFile.getName(), new FileDownloadUtils.OnDownloadListener() {
            @Override
            public void onSuccess(File localFile, Uri contentUri) {
                Toast.makeText(ConversionOptionsActivity.this, "File saved to Downloads: " + lastConvertedFile.getName(), Toast.LENGTH_SHORT).show();
                FileDownloadUtils.openFile(ConversionOptionsActivity.this, localFile != null ? localFile : lastConvertedFile);
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ConversionOptionsActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareLastConvertedFile() {
        if (lastConvertedFile == null || !lastConvertedFile.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastConvertedFile);
            String ext = DocumentUtils.getFileExtension(lastConvertedFile.getName());
            String mimeType = DocumentUtils.getMimeType(ext);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType != null ? mimeType : "*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(android.content.ClipData.newRawUri(lastConvertedFile.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, "Share converted file");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendFileToShopkeeper() {
        if (lastConvertedFile == null || !lastConvertedFile.exists()) {
            Toast.makeText(this, "No converted file available", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ShopListActivity.class);
        intent.putExtra("extra_file_path", lastConvertedFile.getAbsolutePath());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        executor.shutdown();
    }
}
