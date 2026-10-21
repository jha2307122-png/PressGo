package com.pressgo.app.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.pressgo.app.databinding.ActivityFileCompressionBinding;
import com.pressgo.app.models.CompressionRecord;
import com.pressgo.app.repositories.CompressionRepository;
import com.pressgo.app.utils.DocumentUtils;
import com.pressgo.app.utils.FileDownloadUtils;
import com.pressgo.app.utils.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileCompressionActivity extends AppCompatActivity {
    private static final String TAG = "FileCompression";

    private ActivityFileCompressionBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Uri selectedPdfUri;
    private Uri selectedImageUri;
    private final ArrayList<Uri> selectedZipUris = new ArrayList<>();

    private File lastCompressedFile;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedPdfUri = result.getData().getData();
                    if (selectedPdfUri != null) {
                        String name = DocumentUtils.getFileName(this, selectedPdfUri);
                        long size = DocumentUtils.getFileSize(this, selectedPdfUri);
                        binding.tvSelectedPdf.setText(name + " (" + DocumentUtils.formatFileSize(size) + ")");
                        binding.btnCompressPdf.setEnabled(true);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        String name = DocumentUtils.getFileName(this, selectedImageUri);
                        long size = DocumentUtils.getFileSize(this, selectedImageUri);
                        binding.tvSelectedImage.setText(name + " (" + DocumentUtils.formatFileSize(size) + ")");
                        binding.btnCompressImage.setEnabled(true);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> zipPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedZipUris.clear();
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            selectedZipUris.add(clip.getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        selectedZipUris.add(data.getData());
                    }
                    binding.tvSelectedZipFiles.setText(selectedZipUris.size() + " file(s) selected");
                    binding.btnCreateZip.setEnabled(!selectedZipUris.isEmpty());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityFileCompressionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Compress Files & Create Archives");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
    }

    private void setupListeners() {
        binding.btnSelectPdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/pdf");
            pdfPickerLauncher.launch(intent);
        });

        binding.btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        binding.btnSelectZipFiles.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            zipPickerLauncher.launch(intent);
        });

        binding.btnCompressPdf.setOnClickListener(v -> compressPdf());
        binding.btnCompressImage.setOnClickListener(v -> compressImage());
        binding.btnCreateZip.setOnClickListener(v -> createZip());

        binding.btnDownloadResult.setOnClickListener(v -> downloadLastFile());
        binding.btnShareResult.setOnClickListener(v -> shareLastFile());
        binding.btnSendToShopkeeper.setOnClickListener(v -> sendLastFileToShopkeeper());

        binding.btnViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConversionHistoryActivity.class);
            intent.putExtra(ConversionHistoryActivity.EXTRA_INITIAL_TAB, ConversionHistoryActivity.TAB_COMPRESSED);
            startActivity(intent);
        });

        binding.btnOpenCompressionHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConversionHistoryActivity.class);
            intent.putExtra(ConversionHistoryActivity.EXTRA_INITIAL_TAB, ConversionHistoryActivity.TAB_COMPRESSED);
            startActivity(intent);
        });
    }

    private void downloadLastFile() {
        if (lastCompressedFile == null || !lastCompressedFile.exists()) {
            Toast.makeText(this, "No file available to download", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.saveFileToDownloads(this, lastCompressedFile, lastCompressedFile.getName(), new FileDownloadUtils.OnDownloadListener() {
            @Override
            public void onSuccess(File localFile, Uri contentUri) {
                Toast.makeText(FileCompressionActivity.this, "File downloaded: " + lastCompressedFile.getName(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(FileCompressionActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void compressPdf() {
        if (selectedPdfUri == null) return;
        showLoading(true, "Compressing PDF...");

        executor.execute(() -> {
            try {
                long originalSize = DocumentUtils.getFileSize(this, selectedPdfUri);
                File tempDir = DocumentUtils.getTempDir(this);
                File output = new File(tempDir, "compressed_" + System.currentTimeMillis() + ".pdf");

                InputStream is = getContentResolver().openInputStream(selectedPdfUri);
                PdfReader reader = new PdfReader(is);
                WriterProperties props = new WriterProperties();
                props.setFullCompressionMode(true);
                props.setCompressionLevel(9);

                PdfWriter writer = new PdfWriter(new FileOutputStream(output), props);
                PdfDocument pdfDoc = new PdfDocument(reader, writer);
                pdfDoc.close();

                long newSize = output.length();
                double reduction = originalSize > 0 ? (1.0 - (double) newSize / originalSize) * 100 : 0;
                String inputName = DocumentUtils.getFileName(this, selectedPdfUri);
                saveCompressionHistory(inputName, output.getName(), "PDF", originalSize, newSize, reduction, output.getAbsolutePath());

                runOnUiThread(() -> {
                    showLoading(false, null);
                    lastCompressedFile = output;
                    showResult("PDF Compression", originalSize, newSize, reduction);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error compressing PDF", e);
                runOnUiThread(() -> {
                    showLoading(false, null);
                    Toast.makeText(this, "Failed to compress PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void compressImage() {
        if (selectedImageUri == null) return;
        showLoading(true, "Compressing Image...");

        executor.execute(() -> {
            try {
                long originalSize = DocumentUtils.getFileSize(this, selectedImageUri);
                File tempDir = DocumentUtils.getTempDir(this);
                File output = new File(tempDir, "compressed_" + System.currentTimeMillis() + ".jpg");

                InputStream is = getContentResolver().openInputStream(selectedImageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);

                int quality = 60; // 60% quality compression
                try (FileOutputStream fos = new FileOutputStream(output)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                }

                long newSize = output.length();
                double reduction = originalSize > 0 ? (1.0 - (double) newSize / originalSize) * 100 : 0;
                String inputName = DocumentUtils.getFileName(this, selectedImageUri);
                saveCompressionHistory(inputName, output.getName(), "IMAGE", originalSize, newSize, reduction, output.getAbsolutePath());

                runOnUiThread(() -> {
                    showLoading(false, null);
                    lastCompressedFile = output;
                    showResult("Image Compression", originalSize, newSize, reduction);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error compressing image", e);
                runOnUiThread(() -> {
                    showLoading(false, null);
                    Toast.makeText(this, "Failed to compress image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void createZip() {
        if (selectedZipUris.isEmpty()) return;
        showLoading(true, "Creating ZIP archive...");

        executor.execute(() -> {
            try {
                File tempDir = DocumentUtils.getTempDir(this);
                File output = new File(tempDir, "archive_" + System.currentTimeMillis() + ".zip");

                long originalSize = 0;
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
                    for (Uri uri : selectedZipUris) {
                        String name = DocumentUtils.getFileName(this, uri);
                        originalSize += DocumentUtils.getFileSize(this, uri);
                        zos.putNextEntry(new ZipEntry(name));
                        try (InputStream is = getContentResolver().openInputStream(uri)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }

                long newSize = output.length();
                double reduction = originalSize > 0 ? (1.0 - (double) newSize / originalSize) * 100 : 0;
                final long finalOriginalSize = originalSize;
                String inputName = selectedZipUris.size() == 1
                        ? DocumentUtils.getFileName(this, selectedZipUris.get(0))
                        : selectedZipUris.size() + " files";
                saveCompressionHistory(inputName, output.getName(), "ZIP", finalOriginalSize, newSize, reduction, output.getAbsolutePath());

                runOnUiThread(() -> {
                    showLoading(false, null);
                    lastCompressedFile = output;
                    showResult("ZIP Archive Created", finalOriginalSize, newSize, reduction);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error creating ZIP", e);
                runOnUiThread(() -> {
                    showLoading(false, null);
                    Toast.makeText(this, "Failed to create ZIP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveCompressionHistory(String inputName, String outputName, String fileType,
                                        long origSize, long newSize, double reduction, String localPath) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : new SessionManager(this).getUserId();
        if (uid == null || uid.isEmpty()) {
            uid = "guest_user";
        }

        CompressionRecord record = new CompressionRecord(
                null,
                uid,
                inputName,
                outputName,
                fileType,
                origSize,
                newSize,
                CompressionRecord.LEVEL_HIGH,
                Math.max(0, origSize - newSize),
                reduction,
                CompressionRecord.STATUS_SUCCESS,
                localPath,
                Timestamp.now()
        );

        CompressionRepository.getInstance().saveCompressionRecord(record, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Compression record saved successfully");
            } else {
                Log.w(TAG, "Failed to save compression record", task.getException());
            }
        });
    }

    private void showResult(String title, long origSize, long newSize, double reduction) {
        binding.cardResult.setVisibility(View.VISIBLE);
        binding.tvResultTitle.setText(title + " Complete");
        binding.tvResultDetails.setText(String.format(Locale.US,
                "Original: %s\nOutput: %s\nReduction: %.1f%%",
                DocumentUtils.formatFileSize(origSize),
                DocumentUtils.formatFileSize(newSize),
                reduction
        ));
    }

    private void shareLastFile() {
        if (lastCompressedFile == null || !lastCompressedFile.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastCompressedFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getContentResolver().getType(uri) != null ? getContentResolver().getType(uri) : "*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share compressed file"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendLastFileToShopkeeper() {
        if (lastCompressedFile == null || !lastCompressedFile.exists()) {
            Toast.makeText(this, "No file available", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ShopListActivity.class);
        intent.putExtra("extra_file_path", lastCompressedFile.getAbsolutePath());
        startActivity(intent);
    }

    private void showLoading(boolean show, String msg) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && msg != null) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        executor.shutdown();
    }
}
