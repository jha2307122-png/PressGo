package com.pressgo.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.pressgo.app.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Document-related utility methods: MIME types, file info, temp files.
 */
public final class DocumentUtils {

    private static final String TAG = "DocumentUtils";

    private DocumentUtils() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Extension & MIME helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the lowercase file extension from a filename.
     *
     * @param fileName e.g. "report.PDF"
     * @return lowercase extension without the dot, or "" if absent
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == fileName.length() - 1) return "";
        return fileName.substring(lastDot + 1).toLowerCase(Locale.US);
    }

    /**
     * Extracts the filename without its extension.
     *
     * @param fileName e.g. "report.PDF"
     * @return name without extension, e.g. "report"
     */
    public static String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) return fileName;
        return fileName.substring(0, lastDot);
    }

    /**
     * Returns the MIME type for a given lowercase extension.
     */
    public static String getMimeType(String extension) {
        if (extension == null) return "application/octet-stream";
        switch (extension.toLowerCase(Locale.US)) {
            case "pdf":  return "application/pdf";
            case "doc":  return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument"
                                 + ".wordprocessingml.document";
            case "ppt":  return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument"
                                 + ".presentationml.presentation";
            case "xls":  return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument"
                                 + ".spreadsheetml.sheet";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png":  return "image/png";
            case "webp": return "image/webp";
            case "gif":  return "image/gif";
            case "zip":  return "application/zip";
            case "txt":
            case "java":
            case "py":
            case "cpp":
            case "c":
            case "h":    return "text/plain";
            case "csv":  return "text/csv";
            case "json": return "application/json";
            case "xml":  return "application/xml";
            case "html": return "text/html";
            default:     return "application/octet-stream";
        }
    }

    /**
     * Returns the set of supported file extensions.
     */
    public static Set<String> getSupportedExtensions() {
        return new HashSet<>(Arrays.asList(Constants.SUPPORTED_EXTENSIONS));
    }

    /**
     * Returns {@code true} if the given filename has a supported extension.
     */
    public static boolean isSupported(String fileName) {
        return getSupportedExtensions().contains(getFileExtension(fileName));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File size formatting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Formats a byte count to a human-readable string (e.g. "2.5 MB").
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes == 0) return "0 B";

        DecimalFormat df = new DecimalFormat("#.##");
        double kb = bytes / 1024.0;
        double mb = kb   / 1024.0;
        double gb = mb   / 1024.0;

        if (gb >= 1) return df.format(gb) + " GB";
        if (mb >= 1) return df.format(mb) + " MB";
        if (kb >= 1) return df.format(kb) + " KB";
        return bytes + " B";
    }

    /**
     * @return {@code true} if {@code bytes} is within the 50 MB limit.
     */
    public static boolean isFileSizeValid(long bytes) {
        return bytes > 0 && bytes <= Constants.MAX_FILE_SIZE_BYTES;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content resolver helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the display name for a content URI using {@link OpenableColumns}.
     */
    public static String getFileName(Context context, Uri uri) {
        if (uri == null) return "";
        String result = null;

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {
                Log.e(TAG, "getFileName: error querying URI", e);
            }
        }

        if (result == null) {
            // Fall back to the last path segment
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) result = path.substring(cut + 1);
            }
        }
        return result != null ? result : "";
    }

    /**
     * Returns the byte size of the file at the given URI, or -1 if unknown.
     */
    public static long getFileSize(Context context, Uri uri) {
        if (uri == null) return -1;

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        return cursor.getLong(idx);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getFileSize: error querying URI", e);
            }
        }

        // Fallback via ParcelFileDescriptor
        try (ParcelFileDescriptor pfd =
                     context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) return pfd.getStatSize();
        } catch (Exception e) {
            Log.e(TAG, "getFileSize: fallback failed", e);
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF page count
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the page count for a PDF URI using the system {@link PdfRenderer}.
     *
     * @return Page count, or -1 if the file is not a valid PDF or an error occurs.
     */
    public static int getPdfPageCount(Context context, Uri uri) {
        if (context == null || uri == null) return -1;

        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer     = null;
        try {
            pfd      = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return -1;
            renderer = new PdfRenderer(pfd);
            return renderer.getPageCount();
        } catch (Exception e) {
            Log.e(TAG, "getPdfPageCount: could not render PDF", e);
            return -1;
        } finally {
            if (renderer != null) {
                try { renderer.close(); } catch (Exception ignored) {}
            }
            if (pfd != null) {
                try { pfd.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Output / temp file helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a unique output filename by appending a timestamp.
     * Example: {@code generateOutputFileName("report", "pdf")} → {@code "report_1234567890.pdf"}
     */
    public static String generateOutputFileName(String baseName, String extension) {
        long timestamp = System.currentTimeMillis();
        String base    = (baseName != null && !baseName.isEmpty()) ? baseName : "file";
        String ext     = (extension != null && !extension.isEmpty()) ? extension : "bin";
        return base + "_" + timestamp + "." + ext;
    }

    /**
     * Returns (and creates if needed) the app's private temp directory.
     */
    public static File getTempDir(Context context) {
        File tempDir = new File(context.getCacheDir(), "pressgo_temp");
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) Log.w(TAG, "getTempDir: failed to create temp dir");
        }
        return tempDir;
    }

    /**
     * Creates a new temporary file in the app's temp directory.
     *
     * @param prefix    File name prefix
     * @param extension Extension without the dot
     * @return The created {@link File}, or {@code null} on failure
     */
    public static File createTempFile(Context context, String prefix, String extension) {
        try {
            File tempDir = getTempDir(context);
            return File.createTempFile(prefix, "." + extension, tempDir);
        } catch (IOException e) {
            Log.e(TAG, "createTempFile: failed to create temp file", e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blank page detection and removal
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inspects a converted PDF file and removes any completely blank pages.
     * Retains at least one page even if all pages are deemed empty.
     * Uses atomic file replacement safe for Windows file-locking.
     *
     * @param pdfFile Converted PDF file to clean in-place
     */
    public static void removeBlankPagesFromPdf(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0) return;

        File parentDir = pdfFile.getParentFile();
        if (parentDir == null) return;
        File tempOutputFile = new File(parentDir, "clean_" + System.currentTimeMillis() + "_" + pdfFile.getName());

        try {
            List<Integer> nonBlankPages = new ArrayList<>();
            int totalPages;

            try (PdfReader reader = new PdfReader(pdfFile);
                 PdfDocument srcPdf = new PdfDocument(reader)) {
                totalPages = srcPdf.getNumberOfPages();

                if (totalPages <= 1) {
                    return;
                }

                for (int p = 1; p <= totalPages; p++) {
                    PdfPage page = srcPdf.getPage(p);
                    if (!isPageBlank(page)) {
                        nonBlankPages.add(p);
                    }
                }

                // If no pages were blank, or if ALL pages were blank, keep original
                if (nonBlankPages.size() == totalPages || nonBlankPages.isEmpty()) {
                    return;
                }

                // Copy only non-blank pages to clean destination
                try (PdfWriter writer = new PdfWriter(new FileOutputStream(tempOutputFile));
                     PdfDocument destPdf = new PdfDocument(writer)) {
                    srcPdf.copyPagesTo(nonBlankPages, destPdf);
                }
            }

            // At this point both srcPdf and reader are closed and file locks released.
            // Replace original file with cleaned PDF safely across OS platforms.
            if (tempOutputFile.exists() && tempOutputFile.length() > 0) {
                java.nio.file.Files.copy(tempOutputFile.toPath(), pdfFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tempOutputFile.delete();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error cleaning blank pages from PDF: " + t.getMessage());
            if (tempOutputFile.exists()) {
                tempOutputFile.delete();
            }
        }
    }

    /**
     * Returns true if a PDF page has no text, no visual image XObjects, and no drawing operators.
     */
    public static boolean isPageBlank(PdfPage page) {
        if (page == null) return true;

        try {
            // 1. Text check
            String text = PdfTextExtractor.getTextFromPage(page);
            if (text != null && !text.trim().isEmpty()) {
                return false;
            }

            // 2. Content stream painting operators check
            byte[] content = page.getContentBytes();
            if (content == null || content.length == 0) {
                return true;
            }

            String contentStr = new String(content, StandardCharsets.ISO_8859_1);
            String[] tokens = contentStr.split("\\s+");
            for (String token : tokens) {
                if ("Do".equals(token) || "BI".equals(token) || "sh".equals(token)
                        || "f".equals(token) || "F".equals(token) || "f*".equals(token)
                        || "S".equals(token) || "s".equals(token)
                        || "B".equals(token) || "B*".equals(token)
                        || "b".equals(token) || "b*".equals(token)
                        || "Tj".equals(token) || "TJ".equals(token)
                        || "'".equals(token) || "\"".equals(token)) {
                    return false;
                }
            }
        } catch (Throwable ignored) {}

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile image helpers (resilient across local, Base64, and cloud storage)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a profile image into an ImageView safely handling remote URLs, Base64 data URIs,
     * and local fallback files.
     */
    public static void loadProfileImage(Context context, String photoUrl, String userId, ImageView targetView) {
        if (context == null || targetView == null) return;

        // 1. Base64 data URI check: data:image/...;base64,xxxx
        if (photoUrl != null && photoUrl.startsWith("data:image/")) {
            try {
                int commaIdx = photoUrl.indexOf(",");
                if (commaIdx != -1) {
                    String base64 = photoUrl.substring(commaIdx + 1);
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    Glide.with(context)
                            .asBitmap()
                            .load(bytes)
                            .placeholder(R.drawable.ic_profile)
                            .error(R.drawable.ic_profile)
                            .into(targetView);
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Error decoding Base64 profile image", t);
            }
        }

        // 2. Remote URL check: http:// or https://
        if (photoUrl != null && (photoUrl.startsWith("http://") || photoUrl.startsWith("https://"))) {
            Glide.with(context)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(targetView);
            return;
        }

        // 3. Local file check: file:// or fallback to internal storage profile
        if (userId != null && !userId.trim().isEmpty()) {
            File profileDir = new File(context.getFilesDir(), "profiles");
            File localFile = new File(profileDir, "profile_" + userId + ".jpg");
            if (localFile.exists()) {
                Glide.with(context)
                        .load(localFile)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(targetView);
                return;
            }
        }

        // 4. Default placeholder
        Glide.with(context)
                .load(R.drawable.ic_profile)
                .into(targetView);
    }

    /**
     * Saves a profile picture locally and returns an optimized JPEG byte array for Base64 storage.
     */
    public static byte[] saveLocalProfileImage(Context context, Uri imageUri, String userId) {
        if (context == null || imageUri == null) return null;

        try {
            InputStream is = context.getContentResolver().openInputStream(imageUri);
            if (is == null) return null;

            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return null;

            // Scale down to max 512x512 for optimal storage and memory
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            float maxDim = 512f;
            if (width > maxDim || height > maxDim) {
                float scale = Math.min(maxDim / width, maxDim / height);
                int newW = Math.round(width * scale);
                int newH = Math.round(height * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true);
                if (scaled != bmp) {
                    bmp.recycle();
                    bmp = scaled;
                }
            }

            // Save to internal app files directory: files/profiles/profile_{userId}.jpg
            File profileDir = new File(context.getFilesDir(), "profiles");
            if (!profileDir.exists()) profileDir.mkdirs();
            File profileFile = new File(profileDir, "profile_" + userId + ".jpg");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] jpegBytes = baos.toByteArray();

            try (FileOutputStream fos = new FileOutputStream(profileFile)) {
                fos.write(jpegBytes);
                fos.flush();
            }

            return jpegBytes;
        } catch (Throwable t) {
            Log.e(TAG, "Error saving local profile image", t);
            return null;
        }
    }
}
