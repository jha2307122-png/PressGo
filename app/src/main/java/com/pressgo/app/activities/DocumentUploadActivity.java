package com.pressgo.app.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.adapters.SelectedDocumentAdapter;
import com.pressgo.app.databinding.ActivityDocumentUploadBinding;
import com.pressgo.app.models.SelectedDocument;
import com.pressgo.app.models.Shop;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DocumentUtils;

import java.util.ArrayList;

public class DocumentUploadActivity extends AppCompatActivity {
    private static final String TAG = "DocumentUploadActivity";

    private ActivityDocumentUploadBinding binding;
    private final ArrayList<SelectedDocument> selectedDocuments = new ArrayList<>();
    private SelectedDocumentAdapter adapter;
    private String shopId;
    private boolean isShopOpen = true;
    private Shop currentShop;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    handleFileSelection(result.getData());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityDocumentUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopId = getIntent().getStringExtra(Constants.EXTRA_SHOP_ID);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Upload Documents");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupListeners();
        checkIncomingFile();
        checkShopStatus();
        updateUI();
    }

    private void checkIncomingFile() {
        String incomingPath = getIntent().getStringExtra("extra_file_path");
        if (incomingPath != null && !incomingPath.isEmpty()) {
            java.io.File f = new java.io.File(incomingPath);
            if (f.exists()) {
                try {
                    Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            this, getPackageName() + ".fileprovider", f);
                    processUri(uri);
                    adapter.notifyDataSetChanged();
                } catch (Exception e) {
                    Log.e(TAG, "Error resolving incoming file URI", e);
                }
            }
        }
        Uri incomingUri = getIntent().getParcelableExtra("extra_file_uri");
        if (incomingUri != null) {
            processUri(incomingUri);
            adapter.notifyDataSetChanged();
        }
    }

    private void setupRecyclerView() {
        adapter = new SelectedDocumentAdapter(selectedDocuments, (position, document) -> {
            selectedDocuments.remove(position);
            adapter.notifyItemRemoved(position);
            updateUI();
        });
        binding.rvSelectedDocuments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSelectedDocuments.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.cardUploadArea.setOnClickListener(v -> openFilePicker());
        binding.btnSelectFiles.setOnClickListener(v -> openFilePicker());

        binding.btnNext.setOnClickListener(v -> {
            if (!isShopOpen) {
                showShopClosedDialog();
                return;
            }

            if (selectedDocuments.isEmpty()) {
                Toast.makeText(this, "Please select at least one document", Toast.LENGTH_SHORT).show();
                return;
            }

            // Verify all documents are valid
            for (SelectedDocument doc : selectedDocuments) {
                if (doc.getErrorMessage() != null) {
                    Toast.makeText(this, "Please remove invalid files before proceeding", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (shopId == null || shopId.trim().isEmpty()) {
                Intent shopIntent = new Intent(this, ShopListActivity.class);
                shopIntent.putParcelableArrayListExtra("documents", selectedDocuments);
                startActivity(shopIntent);
                return;
            }

            Intent intent = new Intent(this, PrintCustomizationActivity.class);
            intent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
            intent.putParcelableArrayListExtra("documents", selectedDocuments);
            intent.putExtra("documents", selectedDocuments);
            startActivity(intent);
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        String[] mimeTypes = {"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image/jpeg", "image/png"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void handleFileSelection(Intent data) {
        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                processUri(uri);
            }
        } else if (data.getData() != null) {
            processUri(data.getData());
        }
        adapter.notifyDataSetChanged();
        updateUI();
    }

    private void processUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {}

        String fileName = DocumentUtils.getFileName(this, uri);
        long fileSize = DocumentUtils.getFileSize(this, uri);
        String extension = DocumentUtils.getFileExtension(fileName);

        SelectedDocument doc = new SelectedDocument(uri, fileName, extension, fileSize);

        if (!DocumentUtils.isSupported(fileName)) {
            doc.setErrorMessage("Unsupported file format: " + extension);
        } else if (!DocumentUtils.isFileSizeValid(fileSize)) {
            doc.setErrorMessage("File exceeds 50MB limit");
        } else {
            if ("pdf".equalsIgnoreCase(extension)) {
                int pages = DocumentUtils.getPdfPageCount(this, uri);
                if (pages > 0) {
                    doc.setPageCount(pages);
                    doc.setPageCountKnown(true);
                } else {
                    doc.setPageCount(1);
                    doc.setPageCountKnown(false);
                }
            } else if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension) || "png".equalsIgnoreCase(extension)) {
                doc.setPageCount(1);
                doc.setPageCountKnown(true);
            } else if ("docx".equalsIgnoreCase(extension) || "doc".equalsIgnoreCase(extension)) {
                int pages = com.pressgo.app.converters.WordToPdfConverter.estimateWordPageCount(this, uri);
                doc.setPageCount(pages);
                doc.setPageCountKnown(true);
            } else if ("pptx".equalsIgnoreCase(extension) || "ppt".equalsIgnoreCase(extension)) {
                int slides = com.pressgo.app.converters.PptxToPdfConverter.getPptxSlideCount(this, uri);
                doc.setPageCount(slides);
                doc.setPageCountKnown(true);
            } else {
                // Other format - estimated
                doc.setPageCount(1);
                doc.setPageCountKnown(false);
            }
        }

        selectedDocuments.add(doc);
    }

    private void checkShopStatus() {
        if (shopId == null || shopId.isEmpty()) return;
        ShopRepository.getInstance().getShopById(shopId, shop -> {
            if (shop != null) {
                currentShop = shop;
                isShopOpen = shop.isOpen();
                updateUI();
                if (!isShopOpen) {
                    showShopClosedDialog();
                }
            }
        }, e -> Log.w(TAG, "Could not fetch shop status: " + e.getMessage()));
    }

    private void showShopClosedDialog() {
        String name = currentShop != null ? currentShop.getName() : "This shop";
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Shop is Closed")
                .setMessage("\"" + name + "\" is currently closed and not accepting files for printing.\n\nPlease select another shop that is open.")
                .setPositiveButton("Select Another Shop", (dialog, which) -> {
                    Intent intent = new Intent(this, ShopListActivity.class);
                    String incomingPath = getIntent().getStringExtra("extra_file_path");
                    Uri incomingUri = getIntent().getParcelableExtra("extra_file_uri");
                    intent.putExtra("extra_file_path", incomingPath);
                    intent.putExtra("extra_file_uri", incomingUri);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void updateUI() {
        int count = selectedDocuments.size();
        binding.tvFileCount.setText(count + " file(s) selected");

        long totalSize = 0;
        int totalPages = 0;
        for (SelectedDocument doc : selectedDocuments) {
            totalSize += doc.getFileSizeBytes();
            totalPages += doc.getPageCount();
        }

        binding.tvTotalSize.setText("Total size: " + DocumentUtils.formatFileSize(totalSize) +
                " • Est. pages: " + totalPages);

        binding.rvSelectedDocuments.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (!isShopOpen) {
            binding.btnNext.setEnabled(false);
            binding.btnNext.setText("Shop is Closed (Cannot Send File)");
        } else {
            binding.btnNext.setEnabled(count > 0);
            binding.btnNext.setText("Next: Print Settings");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
