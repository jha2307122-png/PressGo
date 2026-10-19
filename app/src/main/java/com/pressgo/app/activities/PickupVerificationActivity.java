package com.pressgo.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.pressgo.app.databinding.ActivityPickupVerificationBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.utils.Constants;

import java.util.Locale;

public class PickupVerificationActivity extends AppCompatActivity {
    private static final String TAG = "PickupVerification";

    private ActivityPickupVerificationBinding binding;
    private String orderId;
    private PrintOrder order;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    verifyScannedToken(result.getContents());
                } else {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPickupVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Pickup QR Verification");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
        if (orderId != null) {
            loadOrder();
        }
        if (getIntent().getBooleanExtra("extra_auto_scan", false)) {
            startBarcodeScan();
        }
    }

    private void setupListeners() {
        binding.btnScanQr.setOnClickListener(v -> startBarcodeScan());
        binding.btnManualVerify.setOnClickListener(v -> showManualVerifyDialog());
    }

    private void loadOrder() {
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().getOrderById(orderId, o -> {
            binding.progressBar.setVisibility(View.GONE);
            order = o;
            binding.tvOrderId.setText("Order #" + order.getId());
            binding.tvCustomer.setText("Customer: " + (order.getCustomerEmail() != null ? order.getCustomerEmail() : order.getCustomerId()));
            binding.tvStatus.setText("Status: " + order.getOrderStatus());

            if (PrintOrder.STATUS_COMPLETED.equals(order.getOrderStatus())) {
                binding.cardResult.setVisibility(View.VISIBLE);
                binding.tvResultStatus.setText("ORDER ALREADY COMPLETED");
                binding.btnScanQr.setEnabled(false);
            }
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load order", Toast.LENGTH_SHORT).show();
        });
    }

    private void startBarcodeScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Align the customer's PressGo pickup QR code inside the viewfinder");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        barcodeLauncher.launch(options);
    }

    private void verifyScannedToken(String scannedContent) {
        if (scannedContent == null || scannedContent.trim().isEmpty()) {
            Toast.makeText(this, "Empty QR code scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        String targetOrderId = orderId;
        String token = scannedContent.trim();

        if (scannedContent.contains(":")) {
            String[] parts = scannedContent.split(":");
            targetOrderId = parts[0].trim();
            if (parts.length > 1) {
                token = parts[1].trim();
            }
        } else if (targetOrderId == null) {
            targetOrderId = scannedContent.trim();
        }

        final String finalOrderId = targetOrderId;
        final String finalToken = token;

        // Fetch order by ID
        OrderRepository.getInstance().getOrderById(finalOrderId, o -> {
            binding.progressBar.setVisibility(View.GONE);
            order = o;
            orderId = finalOrderId;
            displayOrderDetails(order);

            if (PrintOrder.STATUS_COMPLETED.equals(order.getOrderStatus())) {
                showFailureVerification("Notice: This order (#" + order.getId() + ") was ALREADY marked completed and collected.");
            } else {
                showOrderForHandover(order);
            }
        }, e -> {
            // Try lookup by pickupToken in Firestore
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection(Constants.COLLECTION_ORDERS)
                    .whereEqualTo("pickupToken", finalToken)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snap -> {
                        binding.progressBar.setVisibility(View.GONE);
                        if (!snap.isEmpty()) {
                            order = snap.getDocuments().get(0).toObject(PrintOrder.class);
                            if (order != null) {
                                order.setId(snap.getDocuments().get(0).getId());
                                orderId = order.getId();
                                displayOrderDetails(order);
                                showOrderForHandover(order);
                                return;
                            }
                        }
                        showFailureVerification("No matching order found for this QR code. Please check with customer.");
                    })
                    .addOnFailureListener(err -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showFailureVerification("Error scanning QR code: " + err.getMessage());
                    });
        });
    }

    private void displayOrderDetails(PrintOrder o) {
        binding.tvOrderId.setText("Order #" + o.getId());
        binding.tvCustomer.setText("Customer: " + (o.getCustomerEmail() != null ? o.getCustomerEmail() : o.getCustomerId()));
        binding.tvStatus.setText("Status: " + o.getOrderStatus() + " • Payment: " + o.getPaymentStatus());
    }

    private void showOrderForHandover(PrintOrder o) {
        String colorText = o.isColor() ? "Colour Print" : "Black & White (B&W)";
        String sidesText = o.isBothSides() ? "Both sides (Duplex)" : "Single-sided";
        String bindingText = o.isHasBinding() ? "Spiral / Thesis Binding" : (o.isHasStapling() ? "Corner Stapled" : "No Binding");
        String payment = "PAID".equalsIgnoreCase(o.getPaymentStatus()) ? "✅ PAID ONLINE" : "⚠️ UNPAID (Collect Cash)";
        if ("PAID".equalsIgnoreCase(o.getPaymentStatus()) && o.getTransactionRef() != null && !o.getTransactionRef().isEmpty()) {
            payment += " [TrxID: " + o.getTransactionRef() + "]";
        }
        String notesText = (o.getCustomerNotes() != null && !o.getCustomerNotes().trim().isEmpty())
                ? ("• Extra Requests / Comments: \"" + o.getCustomerNotes().trim() + "\"\n")
                : "";

        String details = "📄 Customer: " + (o.getCustomerEmail() != null ? o.getCustomerEmail() : o.getCustomerId()) + "\n\n" +
                "• Mode: " + colorText + "\n" +
                "• Sides: " + sidesText + "\n" +
                "• Finishing: " + bindingText + "\n" +
                notesText +
                "• Copies: " + o.getCopies() + " (" + o.getTotalPages() + " pages)\n" +
                "• Total Amount: BDT " + String.format(Locale.US, "%.2f", o.getEstimatedPrice()) + " (" + payment + ")\n\n" +
                "Confirm that you are handing over the printed documents to the customer.";

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Customer Pickup Verification")
                .setMessage(details)
                .setPositiveButton("Hand Over & Complete", (d, w) -> {
                    completeOrderHandover(o.getId());
                })
                .setNegativeButton("Cancel", null)
                .show();

        String notesShort = (o.getCustomerNotes() != null && !o.getCustomerNotes().trim().isEmpty())
                ? " • Notes: " + o.getCustomerNotes().trim()
                : "";
        showSuccessVerification("QR Verified! " + (o.getCustomerEmail() != null ? o.getCustomerEmail() : "Customer") + "\n" +
                colorText + " • " + sidesText + " • " + bindingText + notesShort + " • " + payment);
    }

    private void completeOrderHandover(String oId) {
        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().updateOrderStatus(oId, PrintOrder.STATUS_COMPLETED, "Picked up by customer via QR scan verification", task -> {
            binding.progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                showSuccessVerification("✅ Order #" + oId + " completed! Printouts handed over to customer.");
                if (order != null) order.setOrderStatus(PrintOrder.STATUS_COMPLETED);
                if (order != null) displayOrderDetails(order);
                Toast.makeText(this, "Order completed successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to update order status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showManualVerifyDialog() {
        if (orderId == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Manual Order Completion")
                .setMessage("Authorize order completion without scanning QR code? (e.g. customer phone battery died)")
                .setPositiveButton("Authorize & Complete", (dialog, which) -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    OrderRepository.getInstance().updateOrderStatus(orderId, PrintOrder.STATUS_COMPLETED, "Manually authorized by staff", task -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showSuccessVerification("Order marked COMPLETED via staff manual override.");
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSuccessVerification(String message) {
        binding.cardResult.setVisibility(View.VISIBLE);
        binding.tvResultStatus.setText("SUCCESSFULLY VERIFIED");
        binding.tvResultDetails.setText(message);
        binding.btnScanQr.setEnabled(false);
    }

    private void showFailureVerification(String message) {
        binding.cardResult.setVisibility(View.VISIBLE);
        binding.tvResultStatus.setText("VERIFICATION REJECTED");
        binding.tvResultDetails.setText(message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
