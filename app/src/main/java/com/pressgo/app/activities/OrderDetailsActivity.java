package com.pressgo.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.adapters.OrderFileAdapter;
import com.pressgo.app.databinding.ActivityOrderDetailsBinding;
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

public class OrderDetailsActivity extends AppCompatActivity {
    private static final String TAG = "OrderDetailsActivity";

    private ActivityOrderDetailsBinding binding;
    private String orderId;
    private PrintOrder order;
    private final List<OrderFile> orderFiles = new ArrayList<>();
    private OrderFileAdapter fileAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityOrderDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        if (orderId == null) {
            Toast.makeText(this, "Order ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Order Details");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupFilesRecyclerView();
        setupButtons();
        loadOrderDetails();
    }

    private void setupFilesRecyclerView() {
        fileAdapter = new OrderFileAdapter(orderFiles, new OrderFileAdapter.OnFileActionListener() {
            @Override
            public void onDownload(OrderFile file) {
                FileDownloadUtils.downloadOrderFile(OrderDetailsActivity.this, file, new FileDownloadUtils.OnDownloadListener() {
                    @Override
                    public void onSuccess(File localFile, Uri contentUri) {
                        Toast.makeText(OrderDetailsActivity.this, "File downloaded: " + file.getFileName(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(OrderDetailsActivity.this, "Download failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onOpen(OrderFile file) {
                FileDownloadUtils.downloadOrderFile(OrderDetailsActivity.this, file, new FileDownloadUtils.OnDownloadListener() {
                    @Override
                    public void onSuccess(File localFile, Uri contentUri) {
                        if (localFile != null && localFile.exists()) {
                            FileDownloadUtils.openFile(OrderDetailsActivity.this, localFile);
                        } else if (contentUri != null) {
                            FileDownloadUtils.openUri(OrderDetailsActivity.this, contentUri, file.getFileName());
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(OrderDetailsActivity.this, "Cannot open file: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        binding.rvOrderFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOrderFiles.setAdapter(fileAdapter);
    }

    private void setupButtons() {
        binding.btnViewReceipt.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReceiptActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });

        binding.btnMakePayment.setOnClickListener(v -> {
            Intent intent = new Intent(this, PaymentActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });

        binding.btnReorder.setOnClickListener(v -> {
            if (order != null) {
                Intent intent = new Intent(this, DocumentUploadActivity.class);
                intent.putExtra(Constants.EXTRA_SHOP_ID, order.getShopId());
                startActivity(intent);
            }
        });
    }

    private void loadOrderDetails() {
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().getOrderById(orderId, o -> {
            binding.progressBar.setVisibility(View.GONE);
            order = o;
            populateUI(order);
            loadOrderFiles();
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private void populateUI(PrintOrder o) {
        binding.tvOrderId.setText(o.getId());
        binding.statusBadge.setStatus(o.getOrderStatus());
        binding.tvShopName.setText(o.getShopName() != null ? o.getShopName() : "Campus Shop");
        binding.tvSubmittedDate.setText(DateUtils.formatTimestamp(o.getSubmittedAt()));

        String printMode = (o.isColor() ? "Color" : "B&W") + " • " + o.getPaperSize() + " • " +
                (o.isBothSides() ? "Double-sided" : "Single-sided") + " • " +
                (o.isPortrait() ? "Portrait" : "Landscape");
        binding.tvPrintSettings.setText(printMode);

        binding.tvCopies.setText(String.format(Locale.US, "%d", o.getCopies()));
        binding.tvPageRange.setText(String.format(Locale.US, "%s (%d pages)", o.getPageRange(), o.getTotalPages()));

        StringBuilder finishing = new StringBuilder();
        if (o.isHasBinding()) finishing.append("Binding  ");
        if (o.isHasStapling()) finishing.append("Stapling  ");
        if (o.isPriority()) finishing.append("Priority Rush  ");
        if (finishing.length() == 0) finishing.append("Standard");
        binding.tvFinishing.setText(finishing.toString());

        String payStatus = o.getPaymentStatus();
        if ("PAID".equalsIgnoreCase(payStatus) && o.getTransactionRef() != null && !o.getTransactionRef().isEmpty()) {
            payStatus += " (TrxID: " + o.getTransactionRef() + ")";
        }
        binding.tvPaymentStatus.setText(payStatus);
        binding.tvTotalAmount.setText(String.format(Locale.US, "BDT %.2f", o.getEstimatedPrice()));

        if (o.getCustomerNotes() != null && !o.getCustomerNotes().isEmpty()) {
            binding.tvCustomerNotes.setVisibility(View.VISIBLE);
            binding.tvCustomerNotes.setText("Extra Requests / Comments: " + o.getCustomerNotes());
        }

        boolean isPaid = "PAID".equalsIgnoreCase(o.getPaymentStatus());
        boolean isReady = PrintOrder.STATUS_READY_FOR_PICKUP.equals(o.getOrderStatus()) ||
                PrintOrder.STATUS_COMPLETED.equals(o.getOrderStatus());

        if (isReady && isPaid) {
            binding.cardQrCode.setVisibility(View.VISIBLE);
            String token = (o.getId() != null ? o.getId() : "") + ":" + (o.getPickupToken() != null ? o.getPickupToken() : "token");
            Bitmap qr = QRCodeUtils.generateQRCode(token, 200, 200);
            if (qr != null) {
                binding.ivPickupQr.setImageBitmap(qr);
            }
        } else {
            binding.cardQrCode.setVisibility(View.GONE);
        }

        // Payment button visible only if unpaid
        boolean isUnpaid = !isPaid;
        binding.btnMakePayment.setVisibility(isUnpaid ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
