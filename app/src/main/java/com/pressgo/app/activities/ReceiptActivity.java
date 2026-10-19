package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.pressgo.app.R;
import com.pressgo.app.databinding.ActivityReceiptBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DateUtils;
import com.pressgo.app.utils.DocumentUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ReceiptActivity extends AppCompatActivity {
    private static final String TAG = "ReceiptActivity";

    private ActivityReceiptBinding binding;
    private String orderId;
    private PrintOrder order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityReceiptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Print Order Receipt");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupButtons();
        loadReceipt();
    }

    private void setupButtons() {
        binding.btnShareReceipt.setOnClickListener(v -> shareReceiptText());
        binding.btnSaveReceipt.setOnClickListener(v -> saveReceiptToFile());
    }

    private void loadReceipt() {
        if (orderId == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().getOrderById(orderId, o -> {
            binding.progressBar.setVisibility(View.GONE);
            order = o;
            populateReceipt(order);
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load receipt", Toast.LENGTH_SHORT).show();
        });
    }

    private void populateReceipt(PrintOrder o) {
        binding.tvReceiptOrderId.setText("Order #" + o.getId());
        binding.tvReceiptDate.setText("Date: " + DateUtils.formatTimestamp(o.getSubmittedAt()));
        binding.tvReceiptShop.setText(o.getShopName() != null ? o.getShopName() : "Campus Shop");
        binding.tvReceiptCustomer.setText("Customer: " + (o.getCustomerEmail() != null ? o.getCustomerEmail() : o.getCustomerId()));

        String printDetails = (o.isColor() ? "Color" : "B&W") + " • " + o.getPaperSize() + " • " +
                (o.isBothSides() ? "Duplex" : "Single") + "\n" +
                "Pages: " + o.getTotalPages() + " | Copies: " + o.getCopies();
        binding.tvReceiptItems.setText(printDetails);

        binding.tvReceiptPaymentStatus.setText("Payment: " + o.getPaymentStatus());
        binding.tvReceiptTotal.setText(String.format(Locale.US, "BDT %.2f", o.getEstimatedPrice()));
    }

    private String buildReceiptText() {
        if (order == null) return "Receipt unavailable";
        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("       PRESSGO CAMPUS PRINT     \n");
        sb.append("         OFFICIAL RECEIPT       \n");
        sb.append("================================\n\n");
        sb.append("Order ID: ").append(order.getId()).append("\n");
        sb.append("Date:     ").append(DateUtils.formatTimestamp(order.getSubmittedAt())).append("\n");
        sb.append("Shop:     ").append(order.getShopName() != null ? order.getShopName() : "Campus Shop").append("\n");
        sb.append("Customer: ").append(order.getCustomerEmail() != null ? order.getCustomerEmail() : order.getCustomerId()).append("\n\n");
        sb.append("--------------------------------\n");
        sb.append("DETAILS:\n");
        sb.append("Mode:    ").append(order.isColor() ? "Color" : "Black & White").append("\n");
        sb.append("Paper:   ").append(order.getPaperSize()).append("\n");
        sb.append("Pages:   ").append(order.getTotalPages()).append("\n");
        sb.append("Copies:  ").append(order.getCopies()).append("\n");
        sb.append("Binding: ").append(order.isHasBinding() ? "Yes" : "No").append("\n");
        sb.append("--------------------------------\n");
        sb.append("TOTAL:   ").append(String.format(Locale.US, "BDT %.2f", order.getEstimatedPrice())).append("\n");
        sb.append("Status:  ").append(order.getPaymentStatus()).append("\n");
        if (order.getTransactionRef() != null && !order.getTransactionRef().isEmpty()) {
            sb.append("TrxID:   ").append(order.getTransactionRef()).append("\n");
        }
        sb.append("\nThank you for using PressGo!\n");
        sb.append("================================\n");
        return sb.toString();
    }

    private void shareReceiptText() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "PressGo Receipt - " + (order != null ? order.getId() : ""));
        shareIntent.putExtra(Intent.EXTRA_TEXT, buildReceiptText());
        startActivity(Intent.createChooser(shareIntent, "Share Receipt Via"));
    }

    private void saveReceiptToFile() {
        try {
            File tempDir = DocumentUtils.getTempDir(this);
            File receiptFile = new File(tempDir, "Receipt_" + (order != null ? order.getId() : System.currentTimeMillis()) + ".txt");
            try (FileOutputStream fos = new FileOutputStream(receiptFile)) {
                fos.write(buildReceiptText().getBytes(StandardCharsets.UTF_8));
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", receiptFile);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, "text/plain");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(viewIntent);
            Toast.makeText(this, "Receipt saved and opened", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error saving receipt file", e);
            Toast.makeText(this, "Error saving receipt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Share").setIcon(R.drawable.ic_profile).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            shareReceiptText();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
