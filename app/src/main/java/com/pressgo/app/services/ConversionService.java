package com.pressgo.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.pressgo.app.R;
import com.pressgo.app.activities.ConversionHistoryActivity;
import com.pressgo.app.models.ConversionRecord;
import com.pressgo.app.repositories.ConversionRepository;
import com.pressgo.app.utils.DocumentUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ConversionService extends Service {
    private static final String TAG = "ConversionService";
    private static final int NOTIF_ID = 2001;
    public static final String ACTION_CONVERT = "com.pressgo.app.action.CONVERT";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_OUTPUT_NAME = "outputName";
    public static final String EXTRA_USER_ID = "userId";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONVERT.equals(intent.getAction())) {
            startForeground(NOTIF_ID, buildProgressNotification("Starting conversion...", 0));

            String type = intent.getStringExtra(EXTRA_TYPE);
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(EXTRA_URIS);
            String outputName = intent.getStringExtra(EXTRA_OUTPUT_NAME);
            String userId = intent.getStringExtra(EXTRA_USER_ID);

            executor.execute(() -> processConversion(type, uris, outputName, userId));
        }
        return START_NOT_STICKY;
    }

    private void processConversion(String type, ArrayList<Uri> uris, String outputName, String userId) {
        ConversionRecord record = new ConversionRecord();
        record.setUserId(userId);
        record.setInputFileType(type);
        record.setStatus(ConversionRecord.STATUS_IN_PROGRESS);

        try {
            if (uris == null || uris.isEmpty()) {
                throw new IllegalArgumentException("No input files provided");
            }

            File tempDir = DocumentUtils.getTempDir(this);
            String inputName = DocumentUtils.getFileName(this, uris.get(0));
            record.setInputFileName(inputName);
            long totalInputBytes = 0;
            for (Uri u : uris) {
                totalInputBytes += DocumentUtils.getFileSize(this, u);
            }
            record.setInputSizeBytes(totalInputBytes);

            updateNotification("Processing conversion...", 40);

            File outputFile = null;
            if ("WORD_TO_PDF".equals(type)) {
                String outName = outputName != null && !outputName.isEmpty()
                        ? (outputName.toLowerCase(java.util.Locale.US).endsWith(".pdf") ? outputName : outputName + ".pdf")
                        : "converted_word_" + System.currentTimeMillis() + ".pdf";
                outputFile = new File(tempDir, outName);
                com.pressgo.app.converters.WordToPdfConverter.convertWordToPdf(this, uris.get(0), outputFile);
                record.setOutputFileType("PDF");
            } else if ("PPTX_TO_PDF".equals(type)) {
                String outName = outputName != null && !outputName.isEmpty()
                        ? (outputName.toLowerCase(java.util.Locale.US).endsWith(".pdf") ? outputName : outputName + ".pdf")
                        : "converted_presentation_" + System.currentTimeMillis() + ".pdf";
                outputFile = new File(tempDir, outName);
                com.pressgo.app.converters.PptxToPdfConverter.convertPptxToPdf(this, uris.get(0), outputFile);
                record.setOutputFileType("PDF");
            } else if ("PDF_TO_WORD".equals(type)) {
                String outName = outputName != null && !outputName.isEmpty()
                        ? (outputName.toLowerCase(java.util.Locale.US).endsWith(".docx") ? outputName : outputName + ".docx")
                        : "converted_doc_" + System.currentTimeMillis() + ".docx";
                outputFile = new File(tempDir, outName);
                com.pressgo.app.converters.PdfToWordConverter.convertPdfToWord(this, uris.get(0), outputFile);
                record.setOutputFileType("DOCX");
            } else if ("PDF_TO_PPTX".equals(type)) {
                String outName = outputName != null && !outputName.isEmpty()
                        ? (outputName.toLowerCase(java.util.Locale.US).endsWith(".pptx") ? outputName : outputName + ".pptx")
                        : "converted_presentation_" + System.currentTimeMillis() + ".pptx";
                outputFile = new File(tempDir, outName);
                com.pressgo.app.converters.PdfToPptxConverter.convertPdfToPptx(this, uris.get(0), outputFile);
                record.setOutputFileType("PPTX");
            } else if ("IMAGES_TO_PDF".equals(type)) {
                outputFile = new File(tempDir, outputName != null ? outputName : "converted_" + System.currentTimeMillis() + ".pdf");
                com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(new PdfWriter(outputFile));
                com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdfDoc);

                float printableWidth = pdfDoc.getDefaultPageSize().getWidth() - doc.getLeftMargin() - doc.getRightMargin();
                float printableHeight = pdfDoc.getDefaultPageSize().getHeight() - doc.getTopMargin() - doc.getBottomMargin();

                int imagesAdded = 0;
                for (Uri uri : uris) {
                    byte[] bytes = readBytesFromUri(uri);
                    if (bytes.length == 0) continue;
                    com.itextpdf.io.image.ImageData imgData = null;
                    try {
                        imgData = com.itextpdf.io.image.ImageDataFactory.create(bytes);
                    } catch (Exception e) {
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                            imgData = com.itextpdf.io.image.ImageDataFactory.create(stream.toByteArray());
                        }
                    }

                    if (imgData != null) {
                        if (imagesAdded > 0) {
                            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        }
                        com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(imgData);
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
            } else if ("TXT_TO_PDF".equals(type)) {
                outputFile = new File(tempDir, outputName != null ? outputName : "text_" + System.currentTimeMillis() + ".pdf");
                com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(new PdfWriter(outputFile));
                com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdfDoc);

                int filesAdded = 0;
                for (Uri uri : uris) {
                    byte[] bytes = readBytesFromUri(uri);
                    if (bytes.length == 0) continue;
                    if (filesAdded > 0) {
                        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                    }
                    if (uris.size() > 1) {
                        doc.add(new Paragraph("File: " + DocumentUtils.getFileName(this, uri)).setBold());
                    }
                    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : text.split("\r?\n")) {
                        doc.add(new Paragraph(line.isEmpty() ? " " : line));
                    }
                    filesAdded++;
                }
                if (filesAdded == 0) {
                    doc.add(new Paragraph("(Empty text document)"));
                }
                doc.close();
                record.setOutputFileType("PDF");
            } else if ("ZIP".equals(type)) {
                outputFile = new File(tempDir, outputName != null ? outputName : "archive_" + System.currentTimeMillis() + ".zip");
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                    java.util.Set<String> usedNames = new java.util.HashSet<>();
                    for (Uri uri : uris) {
                        String name = DocumentUtils.getFileName(this, uri);
                        if (name.isEmpty()) name = "file_" + System.currentTimeMillis();
                        String finalName = name;
                        int c = 1;
                        while (usedNames.contains(finalName)) {
                            String base = DocumentUtils.getFileNameWithoutExtension(name);
                            String ext = DocumentUtils.getFileExtension(name);
                            finalName = base + "_" + c + (ext.isEmpty() ? "" : "." + ext);
                            c++;
                        }
                        usedNames.add(finalName);

                        zos.putNextEntry(new ZipEntry(finalName));
                        byte[] bytes = readBytesFromUri(uri);
                        zos.write(bytes, 0, bytes.length);
                        zos.closeEntry();
                    }
                }
                record.setOutputFileType("ZIP");
            } else if ("PDF_TO_IMAGES".equals(type)) {
                File tempSourcePdf = new File(tempDir, "temp_src_" + System.currentTimeMillis() + ".pdf");
                try (FileOutputStream fos = new FileOutputStream(tempSourcePdf)) {
                    fos.write(readBytesFromUri(uris.get(0)));
                }

                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempSourcePdf, ParcelFileDescriptor.MODE_READ_ONLY);
                if (pfd != null) {
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    int pageCount = renderer.getPageCount();
                    if (pageCount <= 0) {
                        renderer.close();
                        pfd.close();
                        tempSourcePdf.delete();
                        throw new Exception("PDF has 0 pages");
                    }

                    if (pageCount == 1) {
                        outputFile = new File(tempDir, "page1_" + System.currentTimeMillis() + ".png");
                        PdfRenderer.Page page = renderer.openPage(0);
                        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();

                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos);
                        }
                        record.setOutputFileType("PNG");
                    } else {
                        outputFile = new File(tempDir, "pdf_pages_" + System.currentTimeMillis() + ".zip");
                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                            for (int i = 0; i < pageCount; i++) {
                                PdfRenderer.Page page = renderer.openPage(i);
                                Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                page.close();

                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 95, baos);
                                byte[] pageBytes = baos.toByteArray();

                                zos.putNextEntry(new ZipEntry("page_" + (i + 1) + ".png"));
                                zos.write(pageBytes, 0, pageBytes.length);
                                zos.closeEntry();
                            }
                        }
                        record.setOutputFileType("ZIP");
                    }
                    renderer.close();
                    pfd.close();
                }
                tempSourcePdf.delete();
            } else {
                // Default: image format convert
                if (uris.size() == 1) {
                    Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uris.get(0)));
                    outputFile = new File(tempDir, "converted_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    }
                    record.setOutputFileType("JPG");
                } else {
                    outputFile = new File(tempDir, "converted_images_" + System.currentTimeMillis() + ".zip");
                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                        int count = 1;
                        for (Uri uri : uris) {
                            Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                            if (bmp == null) continue;
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                            byte[] imgBytes = baos.toByteArray();

                            zos.putNextEntry(new ZipEntry("image_" + count + ".jpg"));
                            zos.write(imgBytes, 0, imgBytes.length);
                            zos.closeEntry();
                            count++;
                        }
                    }
                    record.setOutputFileType("ZIP");
                }
            }

            if (outputFile != null && outputFile.exists()) {
                record.setOutputFileName(outputFile.getName());
                record.setOutputSizeBytes(outputFile.length());
                record.setLocalOutputPath(outputFile.getAbsolutePath());
                record.setStatus(ConversionRecord.STATUS_SUCCESS);
                showCompleteNotification("Conversion complete: " + outputFile.getName());
            } else {
                throw new Exception("Conversion failed: output file not created");
            }

        } catch (Exception e) {
            Log.e(TAG, "Conversion error", e);
            record.setStatus(ConversionRecord.STATUS_FAILED);
            record.setErrorMessage(e.getMessage());
            showCompleteNotification("Conversion failed: " + e.getMessage());
        } finally {
            ConversionRepository.getInstance().saveConversionRecord(record, task -> {});
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    private Notification buildProgressNotification(String text, int progress) {
        return new NotificationCompat.Builder(this, "general")
                .setContentTitle("PressGo Document Conversion")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_convert)
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text, int progress) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildProgressNotification(text, progress));
        }
    }

    private void showCompleteNotification(String text) {
        Intent intent = new Intent(this, ConversionHistoryActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification notif = new NotificationCompat.Builder(this, "general")
                .setContentTitle("PressGo Conversion")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_convert)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID + 1, notif);
        }
    }

    private byte[] readBytesFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            if (is == null) return new byte[0];
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error reading URI bytes: " + uri, e);
            return new byte[0];
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "general",
                    "General Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
