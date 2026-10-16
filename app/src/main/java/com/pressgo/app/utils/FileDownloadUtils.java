package com.pressgo.app.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.pressgo.app.activities.DocumentPreviewActivity;
import com.pressgo.app.models.OrderFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FileDownloadUtils {
    private static final String TAG = "FileDownloadUtils";
    private static final String SUB_DIR = "PressGo";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FileDownloadUtils() {}

    public interface OnDownloadListener {
        void onSuccess(File localFile, Uri contentUri);
        void onFailure(String errorMessage);
    }

    public static FirebaseStorage getFirebaseStorage() {
        try {
            String bucket = FirebaseApp.getInstance().getOptions().getStorageBucket();
            if (bucket != null && !bucket.isEmpty()) {
                if (!bucket.startsWith("gs://")) {
                    bucket = "gs://" + bucket;
                }
                return FirebaseStorage.getInstance(bucket);
            }
        } catch (Exception ignored) {}
        try {
            return FirebaseStorage.getInstance("gs://pressgo-4913d.firebasestorage.app");
        } catch (Exception ignored) {}
        return FirebaseStorage.getInstance();
    }

    /**
     * Downloads an OrderFile for the customer or shopkeeper.
     * Evaluates localPath, base64Data, downloadUrl, storagePath, and contentUri in order.
     */
    public static void downloadOrderFile(Context context, OrderFile file, OnDownloadListener listener) {
        if (file == null) {
            notifyFailure(listener, "Order file is null", context);
            return;
        }

        String fileName = file.getFileName() != null && !file.getFileName().trim().isEmpty()
                ? file.getFileName().trim()
                : "order_file_" + System.currentTimeMillis();

        mainHandler.post(() -> Toast.makeText(context, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show());

        executor.execute(() -> {
            // 1. Check local file on device (fastest, works offline, same device)
            String localPath = file.getLocalPath();
            if (localPath == null || localPath.isEmpty()) {
                localPath = file.getLocalUri();
            }
            if (localPath != null && !localPath.isEmpty()) {
                try {
                    File localFile = new File(localPath);
                    if (localFile.exists() && localFile.length() > 0) {
                        Log.d(TAG, "File found on local disk: " + localPath);
                        saveFileToDownloads(context, localFile, fileName, listener);
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Local file check failed: " + localPath, e);
                }
            }

            // 1b. Check default app orders directory (works when customer and admin test on same device)
            if (context != null && file.getOrderId() != null && !file.getOrderId().isEmpty()) {
                try {
                    File orderDir = new File(context.getExternalFilesDir(null), "orders/" + file.getOrderId());
                    File defaultLocalFile = new File(orderDir, fileName);
                    if (defaultLocalFile.exists() && defaultLocalFile.length() > 0) {
                        Log.d(TAG, "File found in default local orders dir: " + defaultLocalFile.getAbsolutePath());
                        saveFileToDownloads(context, defaultLocalFile, fileName, listener);
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Check default local orders dir failed", e);
                }
            }

            // 2. Check base64 backup data
            String base64 = file.getBase64Data();
            if (base64 != null && !base64.trim().isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    if (bytes != null && bytes.length > 0) {
                        File tempFile = new File(DocumentUtils.getTempDir(context), "b64_" + System.currentTimeMillis() + "_" + fileName);
                        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                            fos.write(bytes);
                            fos.flush();
                        }
                        Log.d(TAG, "File recovered from Base64 data: " + tempFile.length() + " bytes");
                        saveFileToDownloads(context, tempFile, fileName, listener);
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Base64 decode failed, trying next method", e);
                }
            }

            // 3. Direct HTTP download from downloadUrl
            String url = file.getDownloadUrl();
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                try {
                    Log.d(TAG, "Downloading via direct HTTP from: " + url);
                    File tempFile = new File(DocumentUtils.getTempDir(context), "dl_" + System.currentTimeMillis() + "_" + fileName);
                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(40000);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (InputStream is = conn.getInputStream();
                             FileOutputStream fos = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.flush();
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            Log.d(TAG, "HTTP download successful: " + tempFile.length() + " bytes");
                            saveFileToDownloads(context, tempFile, fileName, listener);
                            return;
                        }
                    } else {
                        Log.w(TAG, "HTTP download returned code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Direct HTTP download failed: " + e.getMessage(), e);
                }

                // 3b. If HTTP failed and URL is Firebase Storage, download via SDK
                if (url.contains("firebasestorage.googleapis.com")) {
                    try {
                        Log.d(TAG, "Downloading via Firebase Storage SDK getReferenceFromUrl: " + url);
                        StorageReference ref = getFirebaseStorage().getReferenceFromUrl(url);
                        File tempFile = new File(DocumentUtils.getTempDir(context), "ref_" + System.currentTimeMillis() + "_" + fileName);
                        Tasks.await(ref.getFile(tempFile));
                        if (tempFile.exists() && tempFile.length() > 0) {
                            Log.d(TAG, "Firebase Storage getReferenceFromUrl successful: " + tempFile.length() + " bytes");
                            saveFileToDownloads(context, tempFile, fileName, listener);
                            return;
                        }
                    } catch (Exception refEx) {
                        Log.w(TAG, "Firebase Storage getReferenceFromUrl failed: " + refEx.getMessage(), refEx);
                    }
                }
            }

            // 4. Download from Firebase Storage via storagePath
            String storagePath = file.getStoragePath();
            if (storagePath != null && !storagePath.isEmpty()) {
                try {
                    Log.d(TAG, "Downloading via Firebase Storage SDK from: " + storagePath);
                    FirebaseStorage storage = getFirebaseStorage();
                    StorageReference ref = storage.getReference().child(storagePath);
                    File tempFile = new File(DocumentUtils.getTempDir(context), "sb_" + System.currentTimeMillis() + "_" + fileName);
                    Tasks.await(ref.getFile(tempFile));
                    if (tempFile.exists() && tempFile.length() > 0) {
                        Log.d(TAG, "Firebase Storage download successful: " + tempFile.length() + " bytes");
                        saveFileToDownloads(context, tempFile, fileName, listener);
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Firebase Storage SDK download failed: " + e.getMessage(), e);
                }
            }

            // 5. Try content URI stream
            String contentUriStr = file.getContentUri();
            if (contentUriStr != null && !contentUriStr.isEmpty()) {
                try {
                    Log.d(TAG, "Trying ContentResolver for URI: " + contentUriStr);
                    Uri contentUri = Uri.parse(contentUriStr);
                    InputStream is = context.getContentResolver().openInputStream(contentUri);
                    if (is != null) {
                        File tempFile = new File(DocumentUtils.getTempDir(context), "cr_" + System.currentTimeMillis() + "_" + fileName);
                        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                            fos.flush();
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            saveFileToDownloads(context, tempFile, fileName, listener);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "ContentResolver stream opening failed: " + contentUriStr, e);
                }
            }

            notifyFailure(listener, "Document could not be downloaded. Please ask customer to re-upload.", context);
        });
    }

    /**
     * Saves a local file into the device's public Downloads folder and notifies listener.
     */
    public static void saveFileToDownloads(Context context, File sourceFile, String targetFileName, OnDownloadListener listener) {
        if (sourceFile == null || !sourceFile.exists()) {
            notifyFailure(listener, "Source file does not exist", context);
            return;
        }

        String fileName = (targetFileName != null && !targetFileName.trim().isEmpty())
                ? targetFileName.trim()
                : sourceFile.getName();

        executor.execute(() -> {
            try {
                String ext = DocumentUtils.getFileExtension(fileName);
                String mimeType = DocumentUtils.getMimeType(ext);

                // Also make a persistent copy in app's internal cache for immediate local opening
                File localAppCopy = new File(DocumentUtils.getTempDir(context), "ready_" + System.currentTimeMillis() + "_" + fileName);
                try (InputStream fis = new FileInputStream(sourceFile);
                     FileOutputStream fos = new FileOutputStream(localAppCopy)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                }

                Uri resultUri = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    resultUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (resultUri != null) {
                        try (InputStream is = new FileInputStream(sourceFile);
                             OutputStream os = context.getContentResolver().openOutputStream(resultUri)) {
                            if (os != null) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    os.write(buffer, 0, len);
                                }
                                os.flush();
                            }
                        }
                        // Finalize IS_PENDING so the file becomes immediately readable by all viewer apps
                        values.clear();
                        values.put(MediaStore.Downloads.IS_PENDING, 0);
                        context.getContentResolver().update(resultUri, values, null, null);
                    }
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File pressGoDir = new File(downloadsDir, SUB_DIR);
                    if (!pressGoDir.exists()) {
                        pressGoDir.mkdirs();
                    }

                    File destFile = new File(pressGoDir, fileName);
                    try (InputStream is = new FileInputStream(sourceFile);
                         OutputStream os = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                    MediaScannerConnection.scanFile(context, new String[]{destFile.getAbsolutePath()}, new String[]{mimeType}, null);
                    resultUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", destFile);
                }

                if (resultUri == null) {
                    resultUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", localAppCopy);
                }

                final Uri finalUri = resultUri;
                notifySuccess(listener, localAppCopy, finalUri, "Saved to Downloads: " + fileName, context);
            } catch (Exception e) {
                Log.e(TAG, "Error in saveFileToDownloads", e);
                // Fallback to app-specific external files dir
                try {
                    File fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (fallbackDir != null && !fallbackDir.exists()) fallbackDir.mkdirs();
                    File destFile = new File(fallbackDir, fileName);
                    try (InputStream is = new FileInputStream(sourceFile);
                         OutputStream os = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                    Uri fallbackUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", destFile);
                    notifySuccess(listener, destFile, fallbackUri, "Saved to app downloads: " + fileName, context);
                } catch (Exception fallbackEx) {
                    notifyFailure(listener, "Failed to download file: " + e.getMessage(), context);
                }
            }
        });
    }

    public static void openFile(Context context, File file) {
        if (context == null) return;
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File does not exist or has been removed", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            String ext = DocumentUtils.getFileExtension(file.getName());
            openUriInternal(context, uri, file.getName(), ext, file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error preparing FileProvider URI for " + file.getAbsolutePath(), e);
            // Fallback directly to in-app preview with file path
            try {
                openInAppPreview(context, null, file.getName(), file.getAbsolutePath());
            } catch (Exception fallbackEx) {
                Toast.makeText(context, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void openUri(Context context, Uri uri, String fileName) {
        if (context == null) return;
        if (uri == null) {
            Toast.makeText(context, "Invalid file URI", Toast.LENGTH_SHORT).show();
            return;
        }
        String ext = DocumentUtils.getFileExtension(fileName);
        openUriInternal(context, uri, fileName, ext, null);
    }

    public static void openInAppPreview(Context context, Uri uri, String fileName) {
        openInAppPreview(context, uri, fileName, null);
    }

    public static void openInAppPreview(Context context, File file) {
        if (context == null || file == null || !file.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            openInAppPreview(context, uri, file.getName(), file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed getting FileProvider URI for preview", e);
            openInAppPreview(context, null, file.getName(), file.getAbsolutePath());
        }
    }

    public static void openInAppPreview(Context context, Uri uri, String fileName, String filePath) {
        if (context == null) return;
        try {
            Intent previewIntent = new Intent(context, DocumentPreviewActivity.class);
            if (uri != null) {
                previewIntent.setData(uri);
                previewIntent.putExtra("uri", uri);
                String ext = DocumentUtils.getFileExtension(fileName != null ? fileName : "");
                String mime = DocumentUtils.getMimeType(ext);
                previewIntent.setDataAndType(uri, mime != null ? mime : "*/*");
                previewIntent.setClipData(ClipData.newRawUri(fileName != null ? fileName : "Document", uri));
            }
            if (fileName != null) {
                previewIntent.putExtra("fileName", fileName);
            }
            if (filePath != null) {
                previewIntent.putExtra("filePath", filePath);
                previewIntent.putExtra("extra_file_path", filePath);
            }
            previewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!(context instanceof Activity)) {
                previewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(previewIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed launching DocumentPreviewActivity", e);
            Toast.makeText(context, "Could not launch preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void openUriInternal(Context context, Uri uri, String displayName, String ext, String filePath) {
        List<String> candidateMimeTypes = getCandidateMimeTypes(ext);
        boolean launched = false;

        for (String mimeType : candidateMimeTypes) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                if (uri != null) {
                    intent.setDataAndType(uri, mimeType);
                    intent.setClipData(ClipData.newRawUri(displayName != null ? displayName : "", uri));
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Check whether any external app can actually handle this intent
                List<ResolveInfo> resInfoList = null;
                try {
                    resInfoList = context.getPackageManager().queryIntentActivities(intent, 0);
                } catch (Exception ignored) {}

                if (resInfoList == null || resInfoList.isEmpty()) {
                    continue; // No external handler for this MIME candidate, check next candidate
                }

                // Grant URI permission explicitly to all matching handlers
                if (uri != null) {
                    for (ResolveInfo resolveInfo : resInfoList) {
                        if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                            try {
                                context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                Intent chooser = Intent.createChooser(intent, "Open " + (displayName != null ? displayName : "file"));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (uri != null) {
                    chooser.setClipData(ClipData.newRawUri(displayName != null ? displayName : "", uri));
                }
                if (!(context instanceof Activity)) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(chooser);
                launched = true;
                break;
            } catch (ActivityNotFoundException ex) {
                Log.d(TAG, "No handler found for MIME: " + mimeType + ", trying next candidate");
            } catch (Exception ex) {
                Log.w(TAG, "Failed launching chooser for MIME: " + mimeType, ex);
            }
        }

        if (!launched) {
            // Automatic fallback: Seamlessly open directly in PressGo's built-in DocumentPreviewActivity!
            try {
                openInAppPreview(context, uri, displayName, filePath);
                launched = true;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to launch internal DocumentPreviewActivity fallback", ex);
            }
        }

        if (!launched) {
            String appType = "PDF viewer";
            if ("docx".equalsIgnoreCase(ext) || "doc".equalsIgnoreCase(ext)) appType = "Word / Office reader";
            else if ("pptx".equalsIgnoreCase(ext) || "ppt".equalsIgnoreCase(ext)) appType = "PowerPoint / Office reader";
            else if ("zip".equalsIgnoreCase(ext)) appType = "ZIP extractor";
            Toast.makeText(context, "No app found to open " + (displayName != null ? displayName : "this file") + ".\nPlease install a " + appType + " (e.g. Google Drive, MS Office, or WPS Office).", Toast.LENGTH_LONG).show();
        }
    }

    private static List<String> getCandidateMimeTypes(String ext) {
        List<String> list = new ArrayList<>();
        if (ext == null) {
            list.add("*/*");
            return list;
        }
        String lower = ext.toLowerCase(Locale.US);
        switch (lower) {
            case "pdf":
                list.add("application/pdf");
                break;
            case "docx":
                list.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                list.add("application/msword");
                list.add("application/x-msword");
                break;
            case "doc":
                list.add("application/msword");
                list.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                break;
            case "pptx":
                list.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
                list.add("application/vnd.ms-powerpoint");
                break;
            case "ppt":
                list.add("application/vnd.ms-powerpoint");
                list.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
                break;
            case "jpg":
            case "jpeg":
                list.add("image/jpeg");
                list.add("image/*");
                break;
            case "png":
                list.add("image/png");
                list.add("image/*");
                break;
            case "webp":
                list.add("image/webp");
                list.add("image/*");
                break;
            case "zip":
                list.add("application/zip");
                list.add("application/x-zip-compressed");
                list.add("application/octet-stream");
                break;
            case "txt":
            case "java":
            case "py":
            case "cpp":
            case "c":
            case "h":
                list.add("text/plain");
                list.add("text/*");
                break;
            case "csv":
                list.add("text/csv");
                list.add("text/plain");
                break;
            case "json":
                list.add("application/json");
                list.add("text/plain");
                break;
            case "xml":
                list.add("application/xml");
                list.add("text/xml");
                list.add("text/plain");
                break;
            default:
                String mime = DocumentUtils.getMimeType(lower);
                if (mime != null && !mime.isEmpty()) list.add(mime);
                break;
        }
        list.add("application/octet-stream");
        list.add("*/*");
        return list;
    }

    private static void notifySuccess(OnDownloadListener listener, File localFile, Uri contentUri, String message, Context context) {
        mainHandler.post(() -> {
            if (message != null && context != null) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
            if (listener != null) {
                listener.onSuccess(localFile, contentUri);
            }
        });
    }

    private static void notifyFailure(OnDownloadListener listener, String error, Context context) {
        mainHandler.post(() -> {
            if (error != null && context != null) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
            }
            if (listener != null) {
                listener.onFailure(error);
            }
        });
    }
}
