package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.pressgo.app.R;
import com.pressgo.app.adapters.CompressionHistoryAdapter;
import com.pressgo.app.adapters.ConversionHistoryAdapter;
import com.pressgo.app.databinding.ActivityConversionHistoryBinding;
import com.pressgo.app.models.CompressionRecord;
import com.pressgo.app.models.ConversionRecord;
import com.pressgo.app.repositories.CompressionRepository;
import com.pressgo.app.repositories.ConversionRepository;
import com.pressgo.app.utils.DocumentUtils;
import com.pressgo.app.utils.FileDownloadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConversionHistoryActivity extends AppCompatActivity {
    private static final String TAG = "ConversionHistory";

    public static final String EXTRA_INITIAL_TAB = "initialTab";
    public static final int TAB_CONVERTED = 0;
    public static final int TAB_COMPRESSED = 1;

    private ActivityConversionHistoryBinding binding;

    private final List<ConversionRecord> conversionHistory = new ArrayList<>();
    private final List<CompressionRecord> compressionHistory = new ArrayList<>();

    private ConversionHistoryAdapter conversionAdapter;
    private CompressionHistoryAdapter compressionAdapter;

    private int currentTab = TAB_CONVERTED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityConversionHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("File History");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupAdapters();
        setupTabs();

        int initialTab = getIntent().getIntExtra(EXTRA_INITIAL_TAB, TAB_CONVERTED);
        if (initialTab == TAB_COMPRESSED) {
            TabLayout.Tab tab = binding.tabLayout.getTabAt(TAB_COMPRESSED);
            if (tab != null) tab.select();
        } else {
            switchToTab(TAB_CONVERTED);
        }
    }

    private void setupAdapters() {
        conversionAdapter = new ConversionHistoryAdapter(
                conversionHistory,
                this::showConversionRecordOptions,
                this::downloadConversionRecord
        );

        compressionAdapter = new CompressionHistoryAdapter(
                compressionHistory,
                this::showCompressionRecordOptions,
                this::downloadCompressionRecord
        );

        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            if (currentTab == TAB_CONVERTED) {
                loadConversions();
            } else {
                loadCompressions();
            }
        });
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchToTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (currentTab == TAB_CONVERTED) {
                    loadConversions();
                } else {
                    loadCompressions();
                }
            }
        });
    }

    private void switchToTab(int tabPosition) {
        currentTab = tabPosition;
        if (currentTab == TAB_CONVERTED) {
            binding.rvHistory.setAdapter(conversionAdapter);
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_convert);
            binding.tvEmptyText.setText("No converted files recorded");
            loadConversions();
        } else {
            binding.rvHistory.setAdapter(compressionAdapter);
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_compress);
            binding.tvEmptyText.setText("No compressed files recorded");
            loadCompressions();
        }
    }

    private String getUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        String uid = new com.pressgo.app.utils.SessionManager(this).getUserId();
        return (uid != null && !uid.isEmpty()) ? uid : "guest_user";
    }

    private void loadConversions() {
        String uid = getUserId();
        binding.swipeRefreshLayout.setRefreshing(true);
        ConversionRepository.getInstance().getUserConversions(uid, list -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            conversionHistory.clear();
            if (list != null) {
                conversionHistory.addAll(list);
            }
            conversionAdapter.notifyDataSetChanged();
            updateEmptyState();
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Error loading conversion history", e);
            updateEmptyState();
        });
    }

    private void loadCompressions() {
        String uid = getUserId();
        binding.swipeRefreshLayout.setRefreshing(true);
        CompressionRepository.getInstance().getUserCompressions(uid, list -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            compressionHistory.clear();
            if (list != null) {
                compressionHistory.addAll(list);
            }
            compressionAdapter.notifyDataSetChanged();
            updateEmptyState();
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Error loading compression history", e);
            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        boolean isEmpty = (currentTab == TAB_CONVERTED) ? conversionHistory.isEmpty() : compressionHistory.isEmpty();
        if (isEmpty) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.rvHistory.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.rvHistory.setVisibility(View.VISIBLE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void showConversionRecordOptions(ConversionRecord record) {
        String[] options;
        if (record.getLocalOutputPath() != null && new File(record.getLocalOutputPath()).exists()) {
            options = new String[]{"In-App Preview", "Open in External App", "Download Converted File", "Send File to Shopkeeper for Printing", "Share File", "Delete Entry"};
        } else {
            options = new String[]{"Delete Entry"};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(record.getOutputFileName() != null ? record.getOutputFileName() : "Converted File");
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            if ("In-App Preview".equals(selected)) {
                previewFile(record.getLocalOutputPath());
            } else if ("Open in External App".equals(selected)) {
                openFile(record.getLocalOutputPath());
            } else if ("Download Converted File".equals(selected)) {
                downloadConversionRecord(record);
            } else if ("Send File to Shopkeeper for Printing".equals(selected)) {
                sendFileToShopkeeper(record.getLocalOutputPath());
            } else if ("Share File".equals(selected)) {
                shareFile(record.getLocalOutputPath());
            } else if ("Delete Entry".equals(selected)) {
                deleteConversionRecord(record);
            }
        });
        builder.show();
    }

    private void downloadConversionRecord(ConversionRecord record) {
        if (record == null || record.getLocalOutputPath() == null) {
            Toast.makeText(this, "File not available for download", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(record.getLocalOutputPath());
        if (!f.exists()) {
            Toast.makeText(this, "Local converted file no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.saveFileToDownloads(this, f, record.getOutputFileName(), new FileDownloadUtils.OnDownloadListener() {
            @Override
            public void onSuccess(File localFile, Uri contentUri) {
                Toast.makeText(ConversionHistoryActivity.this, "File saved to Downloads: " + record.getOutputFileName(), Toast.LENGTH_SHORT).show();
                FileDownloadUtils.openFile(ConversionHistoryActivity.this, localFile != null ? localFile : f);
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ConversionHistoryActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteConversionRecord(ConversionRecord record) {
        ConversionRepository.getInstance().deleteConversionRecord(record.getId(), task -> {
            conversionHistory.remove(record);
            conversionAdapter.notifyDataSetChanged();
            updateEmptyState();
            Toast.makeText(this, "Conversion record deleted", Toast.LENGTH_SHORT).show();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compression Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void showCompressionRecordOptions(CompressionRecord record) {
        String[] options;
        if (record.getLocalOutputPath() != null && new File(record.getLocalOutputPath()).exists()) {
            options = new String[]{"In-App Preview", "Open in External App", "Download Compressed File", "Send File to Shopkeeper for Printing", "Share File", "Delete Entry"};
        } else {
            options = new String[]{"Delete Entry"};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(record.getOutputFileName() != null ? record.getOutputFileName() : "Compressed File");
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            if ("In-App Preview".equals(selected)) {
                previewFile(record.getLocalOutputPath());
            } else if ("Open in External App".equals(selected)) {
                openFile(record.getLocalOutputPath());
            } else if ("Download Compressed File".equals(selected)) {
                downloadCompressionRecord(record);
            } else if ("Send File to Shopkeeper for Printing".equals(selected)) {
                sendFileToShopkeeper(record.getLocalOutputPath());
            } else if ("Share File".equals(selected)) {
                shareFile(record.getLocalOutputPath());
            } else if ("Delete Entry".equals(selected)) {
                deleteCompressionRecord(record);
            }
        });
        builder.show();
    }

    private void sendFileToShopkeeper(String localPath) {
        if (localPath == null || !new File(localPath).exists()) {
            Toast.makeText(this, "Local file no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ShopListActivity.class);
        intent.putExtra("extra_file_path", localPath);
        startActivity(intent);
    }

    private void downloadCompressionRecord(CompressionRecord record) {
        if (record == null || record.getLocalOutputPath() == null) {
            Toast.makeText(this, "File not available for download", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(record.getLocalOutputPath());
        if (!f.exists()) {
            Toast.makeText(this, "Local compressed file no longer exists", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.saveFileToDownloads(this, f, record.getOutputFileName(), new FileDownloadUtils.OnDownloadListener() {
            @Override
            public void onSuccess(File localFile, Uri contentUri) {
                Toast.makeText(ConversionHistoryActivity.this, "File saved to Downloads: " + record.getOutputFileName(), Toast.LENGTH_SHORT).show();
                FileDownloadUtils.openFile(ConversionHistoryActivity.this, localFile != null ? localFile : f);
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ConversionHistoryActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteCompressionRecord(CompressionRecord record) {
        CompressionRepository.getInstance().deleteCompressionRecord(record.getId(), task -> {
            compressionHistory.remove(record);
            compressionAdapter.notifyDataSetChanged();
            updateEmptyState();
            Toast.makeText(this, "Compression record deleted", Toast.LENGTH_SHORT).show();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File Viewer / Sharer Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void previewFile(String path) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "File path is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            Toast.makeText(this, "File no longer exists on device cache", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.openInAppPreview(this, f);
    }

    private void openFile(String path) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(this, "File path is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            Toast.makeText(this, "File no longer exists on device cache", Toast.LENGTH_SHORT).show();
            return;
        }
        FileDownloadUtils.openFile(this, f);
    }

    private void shareFile(String path) {
        if (path == null) return;
        try {
            File f = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            String ext = DocumentUtils.getFileExtension(f.getName());
            String mimeType = DocumentUtils.getMimeType(ext);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType != null ? mimeType : "*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(android.content.ClipData.newRawUri(f.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, "Share via");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
            Toast.makeText(this, "Cannot share file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
