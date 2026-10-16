package com.pressgo.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
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
}
