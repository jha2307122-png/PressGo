package com.pressgo.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.adapters.OrderFileAdapter;
import com.pressgo.app.databinding.ActivityAdminOrderDetailsBinding;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DateUtils;
import com.pressgo.app.utils.FileDownloadUtils;
import com.pressgo.app.utils.QRCodeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminOrderDetailsActivity extends AppCompatActivity {
    private static final String TAG = "AdminOrderDetails";

    private ActivityAdminOrderDetailsBinding binding;
    private String orderId;
    private PrintOrder order;
    private final List<OrderFile> orderFiles = new ArrayList<>();
    private OrderFileAdapter fileAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityAdminOrderDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        if (orderId == null || orderId.isEmpty()) {
            orderId = getIntent().getStringExtra("orderId");
        }
        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Order ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manage Order: " + orderId);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupFilesRecyclerView();
        setupActionButtons();
        loadOrder();
    }

    private void setupFilesRecyclerView() {
        fileAdapter = new OrderFileAdapter(orderFiles, new OrderFileAdapter.OnFileActionListener() {
            @Override
            public void onDownload(OrderFile file) {
                FileDownloadUtils.downloadOrderFile(AdminOrderDetailsActivity.this, file, new FileDownloadUtils.OnDownloadListener() {
                    @Override
                    public void onSuccess(File localFile, Uri contentUri) {
                        Toast.makeText(AdminOrderDetailsActivity.this, "File downloaded: " + file.getFileName(), Toast.LENGTH_SHORT).show();
                        if (localFile != null && localFile.exists()) {
                            FileDownloadUtils.openFile(AdminOrderDetailsActivity.this, localFile);
                        } else if (contentUri != null) {
                            FileDownloadUtils.openUri(AdminOrderDetailsActivity.this, contentUri, file.getFileName());
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(AdminOrderDetailsActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onOpen(OrderFile file) {
                FileDownloadUtils.downloadOrderFile(AdminOrderDetailsActivity.this, file, new FileDownloadUtils.OnDownloadListener() {
                    @Override
                    public void onSuccess(File localFile, Uri contentUri) {
                        if (localFile != null && localFile.exists()) {
                            FileDownloadUtils.openFile(AdminOrderDetailsActivity.this, localFile);
                        } else if (contentUri != null) {
                            FileDownloadUtils.openUri(AdminOrderDetailsActivity.this, contentUri, file.getFileName());
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(AdminOrderDetailsActivity.this, "Could not open file: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        binding.rvOrderFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOrderFiles.setAdapter(fileAdapter);
    }

    private void loadOrder() {
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().getOrderById(orderId, o -> {
            binding.progressBar.setVisibility(View.GONE);
            order = o;
            populateUI();
            loadOrderFiles();
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error loading order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadOrderFiles() {
        OrderRepository.getInstance().getOrderFiles(orderId, files -> {
            orderFiles.clear();
            if (files != null && !files.isEmpty()) {
                orderFiles.addAll(files);
                binding.tvFilesCount.setText(files.size() + " file(s)");
                binding.tvNoFiles.setVisibility(View.GONE);
                binding.rvOrderFiles.setVisibility(View.VISIBLE);
            } else {
                binding.tvFilesCount.setText("0 files");
                binding.tvNoFiles.setVisibility(View.VISIBLE);
                binding.rvOrderFiles.setVisibility(View.GONE);
            }
            fileAdapter.notifyDataSetChanged();
        }, e -> {
            Log.e(TAG, "Failed to load order files", e);
        });
    }

    private void populateUI() {
        if (order == null) return;

        binding.tvOrderId.setText("Order #" + order.getId());
        binding.statusBadge.setStatus(order.getOrderStatus());
        binding.tvCustomerInfo.setText("Customer: " + (order.getCustomerEmail() != null ? order.getCustomerEmail() : order.getCustomerId()));
        binding.tvSubmittedDate.setText("Submitted: " + DateUtils.formatTimestamp(order.getSubmittedAt()));

        String printDetails = (order.isColor() ? "Color" : "B&W") + " • " + order.getPaperSize() + " • " +
                (order.isBothSides() ? "Duplex" : "Single-sided") + " • " +
                (order.isPortrait() ? "Portrait" : "Landscape") + "\n" +
                "Pages: " + order.getTotalPages() + " | Copies: " + order.getCopies() + " | Range: " + order.getPageRange();
        binding.tvPrintSettings.setText(printDetails);

        StringBuilder finishing = new StringBuilder();
        if (order.isHasBinding()) finishing.append("Binding  ");
        if (order.isHasStapling()) finishing.append("Stapling  ");
        if (order.isPriority()) finishing.append("Priority Rush  ");
        if (finishing.length() == 0) finishing.append("None");
        binding.tvFinishing.setText("Finishing: " + finishing.toString());

        binding.tvAmount.setText(String.format(Locale.US, "BDT %.2f", order.getEstimatedPrice()));
        String paymentInfo = "Payment: " + order.getPaymentStatus();
        if (order.getTransactionRef() != null && !order.getTransactionRef().isEmpty()) {
            paymentInfo += " (TrxID: " + order.getTransactionRef() + ")";
        }
        binding.tvPaymentStatus.setText(paymentInfo);

        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            binding.tvCustomerNotes.setVisibility(View.VISIBLE);
            binding.tvCustomerNotes.setText("Customer Notes: " + order.getCustomerNotes());
            binding.cardCustomerNotesAlert.setVisibility(View.VISIBLE);
            binding.tvCustomerNotesAlert.setText(order.getCustomerNotes());
        } else {
            binding.tvCustomerNotes.setVisibility(View.GONE);
            binding.cardCustomerNotesAlert.setVisibility(View.GONE);
        }

        if (order.getStaffNotes() != null) {
            binding.etStaffNotes.setText(order.getStaffNotes());
        }

        updateActionButtonsForStatus(order.getOrderStatus());
    }

    private void updateActionButtonsForStatus(String status) {
        binding.btnAccept.setVisibility(View.GONE);
        binding.btnReject.setVisibility(View.GONE);
        binding.btnStartPrinting.setVisibility(View.GONE);
        binding.btnMarkReady.setVisibility(View.GONE);
        binding.btnVerifyPickup.setVisibility(View.GONE);

        if (PrintOrder.STATUS_PENDING.equals(status)) {
            binding.btnAccept.setVisibility(View.VISIBLE);
            binding.btnReject.setVisibility(View.VISIBLE);
        } else if (PrintOrder.STATUS_ACCEPTED.equals(status)) {
            binding.btnStartPrinting.setVisibility(View.VISIBLE);
        } else if (PrintOrder.STATUS_PRINTING.equals(status)) {
            binding.btnMarkReady.setVisibility(View.VISIBLE);
        } else if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(status)) {
            binding.btnVerifyPickup.setVisibility(View.VISIBLE);
        }
    }

    private void setupActionButtons() {
        binding.btnAccept.setOnClickListener(v -> updateStatus(PrintOrder.STATUS_ACCEPTED, "Order accepted by staff"));

        binding.btnReject.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Reject Order");
            final EditText input = new EditText(this);
            input.setHint("Rejection reason (required)");
            builder.setView(input);
            builder.setPositiveButton("Reject", (dialog, which) -> {
                String reason = input.getText().toString().trim();
                updateStatus(PrintOrder.STATUS_REJECTED, reason.isEmpty() ? "Unfulfillable order" : reason);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });

        binding.btnStartPrinting.setOnClickListener(v -> updateStatus(PrintOrder.STATUS_PRINTING, "Job sent to printer"));

        binding.btnMarkReady.setOnClickListener(v -> updateStatus(PrintOrder.STATUS_READY_FOR_PICKUP, "Printing finished, ready on shelf"));

        binding.btnVerifyPickup.setOnClickListener(v -> {
            Intent intent = new Intent(this, PickupVerificationActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });

        binding.btnSaveNotes.setOnClickListener(v -> {
            String notes = binding.etStaffNotes.getText() != null ? binding.etStaffNotes.getText().toString().trim() : "";
            binding.progressBar.setVisibility(View.VISIBLE);
            OrderRepository.getInstance().updateOrderStatus(orderId, order.getOrderStatus(), notes, task -> {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Staff notes saved", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void updateStatus(String newStatus, String notes) {
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().updateOrderStatus(orderId, newStatus, notes, task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                Toast.makeText(this, "Order updated to " + newStatus, Toast.LENGTH_SHORT).show();
                loadOrder();
            } else {
                Toast.makeText(this, "Failed to update order", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrder();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
