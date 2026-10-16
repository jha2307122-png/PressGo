package com.pressgo.app.activities;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.pressgo.app.converters.PptxToPdfConverter;
import com.pressgo.app.converters.WordToPdfConverter;
import com.pressgo.app.databinding.ActivityDocumentPreviewBinding;
import com.pressgo.app.utils.DocumentUtils;
import com.pressgo.app.utils.FileDownloadUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DocumentPreviewActivity extends AppCompatActivity {
    private static final String TAG = "DocumentPreviewActivity";

    private ActivityDocumentPreviewBinding binding;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor pfd;
    private int pageIndex = 0;

    private Uri currentUri;
    private String currentFileName = "Document";
    private String currentFilePath;
    private File tempPreviewFile;
    private File tempZipExtractDir;

    // ZIP archive preview state
    private final List<File> zipExtractedFiles = new ArrayList<>();
    private int currentZipIndex = 0;
    private boolean isZipMode = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityDocumentPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Document Preview");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Safe parameter extraction across all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentUri = getIntent().getParcelableExtra("uri", Uri.class);
        } else {
            currentUri = getIntent().getParcelableExtra("uri");
        }
        if (currentUri == null) {
            currentUri = getIntent().getData();
        }

        currentFilePath = getIntent().getStringExtra("filePath");
        if (currentFilePath == null && getIntent().hasExtra("extra_file_path")) {
            currentFilePath = getIntent().getStringExtra("extra_file_path");
        }

        currentFileName = getIntent().getStringExtra("fileName");
        if ((currentFileName == null || currentFileName.trim().isEmpty()) && currentFilePath != null) {
            currentFileName = new File(currentFilePath).getName();
        }
        if ((currentFileName == null || currentFileName.trim().isEmpty()) && currentUri != null) {
            currentFileName = DocumentUtils.getFileName(this, currentUri);
        }
        if (currentFileName == null || currentFileName.trim().isEmpty()) {
            currentFileName = "Document Preview";
        }

        binding.tvDocTitle.setText(currentFileName);

        // Resolve missing URI from existing file path
        if (currentUri == null && currentFilePath != null) {
            File f = new File(currentFilePath);
            if (f.exists()) {
                try {
                    currentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                } catch (Exception e) {
                    currentUri = Uri.fromFile(f);
                }
            }
        }

        // Resolve missing file path from file:// scheme URI
        if (currentFilePath == null && currentUri != null && "file".equalsIgnoreCase(currentUri.getScheme())) {
            currentFilePath = currentUri.getPath();
        }

        if (currentUri != null || (currentFilePath != null && new File(currentFilePath).exists())) {
            loadDocument(currentUri, currentFileName, currentFilePath);
        } else {
            Toast.makeText(this, "No document available to preview", Toast.LENGTH_SHORT).show();
            finish();
        }

        binding.btnPrevPage.setOnClickListener(v -> {
            if (isZipMode) {
                if (currentZipIndex > 0) {
                    displayZipItem(currentZipIndex - 1);
                }
            } else if (pdfRenderer != null && pageIndex > 0) {
                showPage(pageIndex - 1);
            }
        });

        binding.btnNextPage.setOnClickListener(v -> {
            if (isZipMode) {
                if (currentZipIndex < zipExtractedFiles.size() - 1) {
                    displayZipItem(currentZipIndex + 1);
                }
            } else if (pdfRenderer != null && pageIndex < pdfRenderer.getPageCount() - 1) {
                showPage(pageIndex + 1);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 101, 0, "Open in External App").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 102, 1, "Share File").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 101) {
            if (currentFilePath != null && new File(currentFilePath).exists()) {
                FileDownloadUtils.openFile(this, new File(currentFilePath));
            } else if (currentUri != null) {
                FileDownloadUtils.openUri(this, currentUri, currentFileName);
            }
            return true;
        } else if (item.getItemId() == 102) {
            shareDocument();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareDocument() {
        Uri shareUri = currentUri;
        if (shareUri == null && currentFilePath != null) {
            File f = new File(currentFilePath);
            if (f.exists()) {
                try {
                    shareUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                } catch (Exception ignored) {}
            }
        }
        if (shareUri == null) {
            Toast.makeText(this, "Cannot share document", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            String ext = DocumentUtils.getFileExtension(currentFileName);
            String mime = DocumentUtils.getMimeType(ext);
            shareIntent.setType(mime != null ? mime : "*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.setClipData(ClipData.newRawUri(currentFileName, shareUri));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share " + currentFileName));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDocument(Uri uri, String fileName, String filePath) {
        String ext = DocumentUtils.getFileExtension(fileName != null ? fileName : "").toLowerCase(Locale.US);

        binding.progressBar.setVisibility(View.GONE);
        binding.controlsLayout.setVisibility(View.GONE);
        binding.ivPreview.setVisibility(View.GONE);
        binding.scrollTextContent.setVisibility(View.GONE);
        binding.tvUnsupported.setVisibility(View.GONE);

        if ("pdf".equals(ext)) {
            loadPdfDirect(uri, filePath);
        } else if ("docx".equals(ext) || "doc".equals(ext)) {
            loadOfficeDocumentAsPdf(uri, filePath, ext, true);
        } else if ("pptx".equals(ext) || "ppt".equals(ext)) {
            loadOfficeDocumentAsPdf(uri, filePath, ext, false);
        } else if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext) || "gif".equals(ext) || "bmp".equals(ext)) {
            loadImageDocument(uri, filePath);
        } else if ("zip".equals(ext)) {
            loadZipArchive(uri, filePath);
        } else if ("txt".equals(ext) || "csv".equals(ext) || "json".equals(ext) || "xml".equals(ext)
                || "java".equals(ext) || "py".equals(ext) || "cpp".equals(ext) || "c".equals(ext) || "h".equals(ext) || "html".equals(ext)) {
            loadTextDocument(uri, filePath);
        } else {
            // Default: attempt PDF first, then fallback to text or image
            loadPdfDirect(uri, filePath);
        }
    }

    private void loadPdfDirect(Uri uri, String filePath) {
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                ParcelFileDescriptor descriptor = null;

                // 1. Direct local file descriptor if path is valid
                if (filePath != null) {
                    File local = new File(filePath);
                    if (local.exists() && local.length() > 0) {
                        try {
                            descriptor = ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY);
                        } catch (Exception ignored) {}
                    }
                }

                // 2. Open via ContentResolver
                if (descriptor == null && uri != null) {
                    try {
                        descriptor = getContentResolver().openFileDescriptor(uri, "r");
                    } catch (Exception ignored) {}
                }

                // 3. Fallback: Copy stream to a clean local cache file
                if (descriptor == null && uri != null) {
                    File cacheCopy = new File(getCacheDir(), "pdf_view_" + System.currentTimeMillis() + ".pdf");
                    try (InputStream is = getContentResolver().openInputStream(uri);
                         FileOutputStream fos = new FileOutputStream(cacheCopy)) {
                        if (is != null) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) > 0) {
                                fos.write(buf, 0, len);
                            }
                            fos.flush();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error copying stream to cacheCopy", e);
                    }
                    if (cacheCopy.exists() && cacheCopy.length() > 0) {
                        tempPreviewFile = cacheCopy;
                        descriptor = ParcelFileDescriptor.open(cacheCopy, ParcelFileDescriptor.MODE_READ_ONLY);
                    }
                }

                if (descriptor != null) {
                    final ParcelFileDescriptor finalPfd = descriptor;
                    mainHandler.post(() -> {
                        try {
                            closeExistingRenderer();
                            pfd = finalPfd;
                            pdfRenderer = new PdfRenderer(pfd);
                            binding.progressBar.setVisibility(View.GONE);
                            binding.ivPreview.setVisibility(View.VISIBLE);
                            binding.controlsLayout.setVisibility(View.VISIBLE);
                            showPage(0);
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing PdfRenderer", e);
                            tryLocalCachePdfFallback(uri);
                        }
                    });
                } else {
                    mainHandler.post(() -> showUnsupported("Could not read document descriptor."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading PDF", e);
                mainHandler.post(() -> showUnsupported("Unable to render preview: " + e.getMessage()));
            }
        });
    }

    private void tryLocalCachePdfFallback(Uri uri) {
        if (uri == null) {
            showUnsupported("Unable to render PDF preview.");
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                File cacheCopy = new File(getCacheDir(), "pdf_fallback_" + System.currentTimeMillis() + ".pdf");
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(cacheCopy)) {
                    if (is != null) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                        fos.flush();
                    }
                }
                if (cacheCopy.exists() && cacheCopy.length() > 0) {
                    tempPreviewFile = cacheCopy;
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(cacheCopy, ParcelFileDescriptor.MODE_READ_ONLY);
                    mainHandler.post(() -> {
                        try {
                            closeExistingRenderer();
                            pfd = fd;
                            pdfRenderer = new PdfRenderer(pfd);
                            binding.progressBar.setVisibility(View.GONE);
                            binding.ivPreview.setVisibility(View.VISIBLE);
                            binding.controlsLayout.setVisibility(View.VISIBLE);
                            showPage(0);
                        } catch (Exception ex) {
                            Log.e(TAG, "Secondary PdfRenderer fallback failed", ex);
                            showUnsupported("Unable to display PDF preview.");
                        }
                    });
                } else {
                    mainHandler.post(() -> showUnsupported("Unable to display PDF preview."));
                }
            } catch (Exception ex) {
                Log.e(TAG, "Secondary fallback exception", ex);
                mainHandler.post(() -> showUnsupported("Unable to display PDF preview."));
            }
        });
    }

    private void loadImageDocument(Uri uri, String filePath) {
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                if (filePath != null) {
                    File f = new File(filePath);
                    if (f.exists() && f.length() > 0) {
                        bmp = decodeSampledBitmapFromFile(f.getAbsolutePath(), 1920, 1920);
                    }
                }
                if (bmp == null && uri != null) {
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        if (is != null) {
                            bmp = BitmapFactory.decodeStream(is);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error decoding image for preview", t);
            }

            final Bitmap finalBmp = bmp;
            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (finalBmp != null) {
                    binding.ivPreview.setVisibility(View.VISIBLE);
                    binding.ivPreview.setImageBitmap(finalBmp);
                } else {
                    showUnsupported("Could not load image.");
                }
            });
        });
    }

    private void loadZipArchive(Uri uri, String filePath) {
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            File extractDir = new File(getCacheDir(), "zip_preview_" + System.currentTimeMillis());
            extractDir.mkdirs();
            tempZipExtractDir = extractDir;

            List<File> extracted = new ArrayList<>();
            try {
                InputStream is = null;
                if (filePath != null) {
                    File src = new File(filePath);
                    if (src.exists()) is = new FileInputStream(src);
                }
                if (is == null && uri != null) {
                    is = getContentResolver().openInputStream(uri);
                }

                if (is != null) {
                    try (ZipInputStream zis = new ZipInputStream(is)) {
                        ZipEntry entry;
                        byte[] buffer = new byte[8192];
                        while ((entry = zis.getNextEntry()) != null) {
                            if (entry.isDirectory()) {
                                zis.closeEntry();
                                continue;
                            }
                            String rawName = entry.getName();
                            String simpleName = new File(rawName).getName();
                            if (simpleName.isEmpty() || simpleName.startsWith(".")) {
                                zis.closeEntry();
                                continue;
                            }
                            File out = new File(extractDir, simpleName);
                            try (FileOutputStream fos = new FileOutputStream(out)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                                fos.flush();
                            }
                            if (out.exists() && out.length() > 0) {
                                extracted.add(out);
                            }
                            zis.closeEntry();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading zip archive for preview", e);
            }

            Collections.sort(extracted, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (!extracted.isEmpty()) {
                    zipExtractedFiles.clear();
                    zipExtractedFiles.addAll(extracted);
                    isZipMode = true;
                    binding.controlsLayout.setVisibility(View.VISIBLE);
                    displayZipItem(0);
                } else {
                    showUnsupported("ZIP Archive contains no previewable files.");
                }
            });
        });
    }

    private void displayZipItem(int index) {
        if (zipExtractedFiles.isEmpty() || index < 0 || index >= zipExtractedFiles.size()) return;
        currentZipIndex = index;
        File currentItem = zipExtractedFiles.get(index);
        String name = currentItem.getName();
        String ext = DocumentUtils.getFileExtension(name).toLowerCase(Locale.US);

        if (name.startsWith("slide_")) {
            binding.tvPageIndicator.setText(String.format(Locale.US, "Slide %d of %d", index + 1, zipExtractedFiles.size()));
        } else {
            binding.tvPageIndicator.setText(String.format(Locale.US, "File %d of %d: %s", index + 1, zipExtractedFiles.size(), name));
        }
        binding.btnPrevPage.setEnabled(index > 0);
        binding.btnNextPage.setEnabled(index < zipExtractedFiles.size() - 1);

        binding.ivPreview.setVisibility(View.GONE);
        binding.scrollTextContent.setVisibility(View.GONE);
        binding.tvUnsupported.setVisibility(View.GONE);

        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext) || "gif".equals(ext) || "bmp".equals(ext)) {
            Bitmap bmp = decodeSampledBitmapFromFile(currentItem.getAbsolutePath(), 1920, 1920);
            if (bmp != null) {
                binding.ivPreview.setVisibility(View.VISIBLE);
                binding.ivPreview.setImageBitmap(bmp);
            } else {
                showUnsupported("Could not decode image: " + name);
            }
        } else if ("pdf".equals(ext)) {
            try {
                closeExistingRenderer();
                pfd = ParcelFileDescriptor.open(currentItem, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pfd);
                binding.ivPreview.setVisibility(View.VISIBLE);
                showPage(0);
            } catch (Exception ex) {
                Log.e(TAG, "Error rendering zip PDF entry", ex);
                showUnsupported("Unable to display PDF page: " + name);
            }
        } else {
            // Text or other format
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(currentItem), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 2000) {
                    sb.append(line).append("\n");
                    count++;
                }
                String text = sb.toString().trim();
                if (!text.isEmpty()) {
                    binding.scrollTextContent.setVisibility(View.VISIBLE);
                    binding.tvTextContent.setText(text);
                } else {
                    showUnsupported("File is empty: " + name);
                }
            } catch (Exception ex) {
                showUnsupported("Unsupported file format: " + name);
            }
        }
    }

    private void loadOfficeDocumentAsPdf(Uri uri, String filePath, String ext, boolean isWord) {
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            File tempPdf = new File(getCacheDir(), "preview_" + System.currentTimeMillis() + ".pdf");
            boolean converted = false;
            try {
                Uri sourceUri = uri;
                if (sourceUri == null && filePath != null) {
                    sourceUri = Uri.fromFile(new File(filePath));
                }
                if (sourceUri != null) {
                    if (isWord) {
                        WordToPdfConverter.convertWordToPdf(this, sourceUri, tempPdf);
                    } else {
                        PptxToPdfConverter.convertPptxToPdf(this, sourceUri, tempPdf);
                    }
                    converted = tempPdf.exists() && tempPdf.length() > 0;
                }
            } catch (Exception e) {
                Log.w(TAG, "Office to PDF conversion for preview failed: " + e.getMessage());
            }

            if (converted) {
                tempPreviewFile = tempPdf;
                try {
                    ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
                    mainHandler.post(() -> {
                        try {
                            closeExistingRenderer();
                            pfd = descriptor;
                            pdfRenderer = new PdfRenderer(pfd);
                            binding.progressBar.setVisibility(View.GONE);
                            binding.ivPreview.setVisibility(View.VISIBLE);
                            binding.controlsLayout.setVisibility(View.VISIBLE);
                            showPage(0);
                        } catch (Exception e) {
                            Log.e(TAG, "Error displaying rendered office PDF", e);
                            fallbackExtractOfficeText(uri, filePath, isWord);
                        }
                    });
                    return;
                } catch (Exception ignored) {}
            }

            // Fallback: extract text directly from document zip archive
            mainHandler.post(() -> fallbackExtractOfficeText(uri, filePath, isWord));
        });
    }

    private void fallbackExtractOfficeText(Uri uri, String filePath, boolean isWord) {
        executor.execute(() -> {
            List<File> extractedSlideImages = new ArrayList<>();
            File extractDir = null;
            if (!isWord) {
                extractDir = new File(getCacheDir(), "pptx_slides_" + System.currentTimeMillis());
                extractDir.mkdirs();
                tempZipExtractDir = extractDir;
            }

            StringBuilder sb = new StringBuilder();
            try {
                InputStream is = null;
                if (filePath != null) {
                    File src = new File(filePath);
                    if (src.exists()) is = new FileInputStream(src);
                }
                if (is == null && uri != null) {
                    is = getContentResolver().openInputStream(uri);
                }

                if (is != null) {
                    byte[] buffer = new byte[8192];
                    try (ZipInputStream zis = new ZipInputStream(is)) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            String entryName = entry.getName();
                            if (isWord && "word/document.xml".equals(entryName)) {
                                String xml = readStreamToString(zis);
                                sb.append(cleanXmlToText(xml));
                                break;
                            } else if (!isWord) {
                                if (entryName.startsWith("ppt/media/slide_") || (entryName.startsWith("ppt/media/") && (entryName.endsWith(".jpg") || entryName.endsWith(".png") || entryName.endsWith(".jpeg")))) {
                                    String simpleName = new File(entryName).getName();
                                    File outImg = new File(extractDir, simpleName);
                                    try (FileOutputStream fos = new FileOutputStream(outImg)) {
                                        int len;
                                        while ((len = zis.read(buffer)) > 0) {
                                            fos.write(buffer, 0, len);
                                        }
                                        fos.flush();
                                    }
                                    if (outImg.exists() && outImg.length() > 0) {
                                        extractedSlideImages.add(outImg);
                                    }
                                } else if (entryName.startsWith("ppt/slides/slide") && entryName.endsWith(".xml")) {
                                    String xml = readStreamToString(zis);
                                    sb.append("--- Slide ---\n").append(cleanXmlToText(xml)).append("\n\n");
                                }
                            }
                            zis.closeEntry();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting fallback office text", e);
            }

            if (!extractedSlideImages.isEmpty()) {
                Collections.sort(extractedSlideImages, (a, b) -> {
                    try {
                        int numA = Integer.parseInt(a.getName().replaceAll("\\D+", ""));
                        int numB = Integer.parseInt(b.getName().replaceAll("\\D+", ""));
                        return Integer.compare(numA, numB);
                    } catch (Exception ignored) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });

                mainHandler.post(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    zipExtractedFiles.clear();
                    zipExtractedFiles.addAll(extractedSlideImages);
                    isZipMode = true;
                    binding.controlsLayout.setVisibility(View.VISIBLE);
                    displayZipItem(0);
                });
                return;
            }

            final String result = sb.toString().trim();
            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (!result.isEmpty()) {
                    binding.scrollTextContent.setVisibility(View.VISIBLE);
                    binding.tvTextContent.setText(result);
                } else {
                    showUnsupported("Document preview not available. Tap 'Open in External App' from the top menu.");
                }
            });
        });
    }

    private void loadTextDocument(Uri uri, String filePath) {
        binding.progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                InputStream is = null;
                if (filePath != null) {
                    File src = new File(filePath);
                    if (src.exists()) is = new FileInputStream(src);
                }
                if (is == null && uri != null) {
                    is = getContentResolver().openInputStream(uri);
                }

                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        int count = 0;
                        while ((line = reader.readLine()) != null && count < 2000) {
                            sb.append(line).append("\n");
                            count++;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading text document", e);
            }

            final String text = sb.toString().trim();
            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (!text.isEmpty()) {
                    binding.scrollTextContent.setVisibility(View.VISIBLE);
                    binding.tvTextContent.setText(text);
                } else {
                    showUnsupported("Text file is empty or could not be read.");
                }
            });
        });
    }

    private static String readStreamToString(InputStream is) throws Exception {
        byte[] buffer = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int len;
        while ((len = is.read(buffer)) > 0) {
            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String cleanXmlToText(String xml) {
        if (xml == null) return "";
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < xml.length()) {
            int tOpen = xml.indexOf("<w:t", start);
            if (tOpen == -1) tOpen = xml.indexOf("<a:t", start);
            if (tOpen == -1) break;

            int tagEnd = xml.indexOf(">", tOpen);
            if (tagEnd == -1) break;

            int tClose = xml.indexOf("</w:t>", tagEnd);
            if (tClose == -1) tClose = xml.indexOf("</a:t>", tagEnd);
            if (tClose == -1) break;

            String text = xml.substring(tagEnd + 1, tClose);
            sb.append(text).append(" ");
            start = tClose + 6;
        }

        String raw = sb.toString().trim();
        if (raw.isEmpty()) {
            return xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
        return raw;
    }

    private void showUnsupported(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.ivPreview.setVisibility(View.GONE);
        binding.controlsLayout.setVisibility(View.GONE);
        binding.scrollTextContent.setVisibility(View.GONE);
        binding.tvUnsupported.setVisibility(View.VISIBLE);
        binding.tvUnsupported.setText(message);
    }

    private void showPage(int index) {
        if (pdfRenderer == null || pdfRenderer.getPageCount() <= index) return;

        if (currentPage != null) {
            try {
                currentPage.close();
            } catch (Exception ignored) {}
            currentPage = null;
        }

        try {
            currentPage = pdfRenderer.openPage(index);
            pageIndex = index;

            int pageW = currentPage.getWidth();
            int pageH = currentPage.getHeight();

            // Calculate scale to ensure sharp rendering while avoiding OutOfMemory
            float scale = 2.0f;
            int maxDim = Math.max(pageW, pageH);
            if (maxDim > 2000) {
                scale = 2000f / maxDim;
            }
            int targetW = Math.max(1, (int) (pageW * scale));
            int targetH = Math.max(1, (int) (pageH * scale));

            Bitmap bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE); // CRITICAL: draw white paper background
            currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            binding.ivPreview.setImageBitmap(bitmap);

            binding.tvPageIndicator.setText(String.format(Locale.US, "Page %d of %d", index + 1, pdfRenderer.getPageCount()));
            binding.btnPrevPage.setEnabled(index > 0);
            binding.btnNextPage.setEnabled(index < pdfRenderer.getPageCount() - 1);
        } catch (Throwable t) {
            Log.e(TAG, "Error rendering PDF page " + index, t);
            Toast.makeText(this, "Error rendering page: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(filePath, options);
        } catch (Throwable t) {
            Log.w(TAG, "Failed decoding sampled bitmap: " + filePath, t);
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private void closeExistingRenderer() {
        try {
            if (currentPage != null) {
                currentPage.close();
                currentPage = null;
            }
            if (pdfRenderer != null) {
                pdfRenderer.close();
                pdfRenderer = null;
            }
            if (pfd != null) {
                pfd.close();
                pfd = null;
            }
        } catch (Exception ignored) {}
    }

    private void deleteDirRecursively(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteDirRecursively(f);
                    } else {
                        f.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeExistingRenderer();
        executor.shutdown();
        if (tempPreviewFile != null && tempPreviewFile.exists()) {
            tempPreviewFile.delete();
        }
        if (tempZipExtractDir != null && tempZipExtractDir.exists()) {
            deleteDirRecursively(tempZipExtractDir);
        }
    }
}
