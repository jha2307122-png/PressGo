package com.pressgo.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.pressgo.app.databinding.ActivityOrderTrackingBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.PaymentRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.DateUtils;
import com.pressgo.app.utils.NotificationUtils;
import com.pressgo.app.utils.QRCodeUtils;

import java.util.Locale;

public class OrderTrackingActivity extends AppCompatActivity {
    private static final String TAG = "OrderTrackingActivity";

    private ActivityOrderTrackingBinding binding;
    private String orderId;
    private PrintOrder currentOrder;
    private String lastStatus;
    private ListenerRegistration orderListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityOrderTrackingBinding.inflate(getLayoutInflater());
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
            getSupportActionBar().setTitle("Track Order: " + orderId);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        NotificationUtils.createNotificationChannels(this);
        NotificationUtils.requestNotificationPermission(this);

        setupListeners();
        startListeningToOrder();
    }

    private void setupListeners() {
        binding.btnCancelOrder.setOnClickListener(v -> showCancelDialog());
        binding.btnViewDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderDetailsActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });
        binding.btnPayOnline.setOnClickListener(v -> showOnlinePaymentDialog());
    }

    private void startListeningToOrder() {
        binding.progressBar.setVisibility(View.VISIBLE);
        orderListener = OrderRepository.getInstance().addOrderStatusListener(orderId, (snapshot, error) -> {
            binding.progressBar.setVisibility(View.GONE);
            if (error != null) {
                Log.e(TAG, "Listen failed", error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                currentOrder = snapshot.toObject(PrintOrder.class);
                if (currentOrder != null) {
                    currentOrder.setId(snapshot.getId());
                    if (lastStatus != null && !lastStatus.equals(currentOrder.getOrderStatus())) {
                        String newStatus = currentOrder.getOrderStatus();
                        if (PrintOrder.STATUS_ACCEPTED.equals(newStatus)) {
                            Toast.makeText(this, "Order Accepted by Shopkeeper!", Toast.LENGTH_SHORT).show();
                            NotificationUtils.createOrderNotification(this, "Order Accepted",
                                    "Your print order has been accepted and added to the printing queue.", orderId);
                        } else if (PrintOrder.STATUS_PRINTING.equals(newStatus)) {
                            Toast.makeText(this, "Printing started!", Toast.LENGTH_SHORT).show();
                            NotificationUtils.createOrderNotification(this, "Printing in Progress",
                                    "Your documents are currently being printed.", orderId);
                        } else if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(newStatus)) {
                            Toast.makeText(this, "🎉 Printout Ready! Pay online to unlock your Pickup QR code.", Toast.LENGTH_LONG).show();
                            NotificationUtils.createOrderNotification(this, "Printout Ready! 📄",
                                    "Your printout is ready! Pay online from the app to receive your pickup QR code.", orderId);
                        } else if (PrintOrder.STATUS_COMPLETED.equals(newStatus)) {
                            Toast.makeText(this, "✅ Order completed! Printout handed over.", Toast.LENGTH_LONG).show();
                            NotificationUtils.createOrderNotification(this, "Order Completed! ✅",
                                    "Your print order has been completed and handed over.", orderId);
                        } else if (PrintOrder.STATUS_REJECTED.equals(newStatus)) {
                            Toast.makeText(this, "Order Rejected", Toast.LENGTH_LONG).show();
                            NotificationUtils.createOrderNotification(this, "Order Rejected",
                                    "Unfortunately, your print order could not be accepted.", orderId);
                        }
                    }
                    lastStatus = currentOrder.getOrderStatus();
                    updateUI(currentOrder);
                }
            }
        });
    }

    private void updateUI(PrintOrder order) {
        binding.orderProgressView.setCurrentStatus(order.getOrderStatus());
        binding.statusBadge.setStatus(order.getOrderStatus());

        binding.tvOrderId.setText(order.getId());
        binding.tvShopName.setText(order.getShopName() != null ? order.getShopName() : "Campus Shop");
        binding.tvSubmittedDate.setText(DateUtils.getTimeAgo(order.getSubmittedAt()));
        binding.tvTotalPrice.setText(String.format(Locale.US, "BDT %.2f", order.getEstimatedPrice()));

        // Queue prediction estimate
        String queueEst = "Estimated pickup in approximately 12 minutes";
        if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(order.getOrderStatus())) {
            queueEst = "Ready for immediate pickup!";
        } else if (PrintOrder.STATUS_COMPLETED.equals(order.getOrderStatus())) {
            queueEst = "Completed on " + DateUtils.formatTimestamp(order.getCompletedAt());
        }
        binding.tvEstimatedTime.setText(queueEst);

        // Customer comments / extra requests
        if (order.getCustomerNotes() != null && !order.getCustomerNotes().trim().isEmpty()) {
            binding.cardCustomerNotes.setVisibility(View.VISIBLE);
            binding.tvOrderCustomerNotes.setText(order.getCustomerNotes().trim());
        } else {
            binding.cardCustomerNotes.setVisibility(View.GONE);
        }

        // Check payment and ready status
        boolean isPaid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
        boolean isReadyOrCompleted = PrintOrder.STATUS_READY_FOR_PICKUP.equals(order.getOrderStatus()) ||
                PrintOrder.STATUS_COMPLETED.equals(order.getOrderStatus());

        if (isReadyOrCompleted) {
            if (!isPaid) {
                // Customer must pay online to get the pickup QR code
                binding.cardPayOnline.setVisibility(View.VISIBLE);
                binding.cardQrCode.setVisibility(View.GONE);
                binding.tvPayAmount.setText(String.format(Locale.US, "Total Amount Due: BDT %.2f", order.getEstimatedPrice()));
            } else {
                // Paid online: Show Pickup QR code
                binding.cardPayOnline.setVisibility(View.GONE);
                binding.cardQrCode.setVisibility(View.VISIBLE);
                String token = (order.getId() != null ? order.getId() : "") + ":" +
                        (order.getPickupToken() != null ? order.getPickupToken() : "token");
                Bitmap qr = QRCodeUtils.generateQRCode(token, 250, 250);
                if (qr != null) {
                    binding.ivPickupQr.setImageBitmap(qr);
                }
                binding.tvPickupInstructions.setText("Show this QR code to the shopkeeper at the counter to verify and collect your printout.");
            }
        } else {
            binding.cardPayOnline.setVisibility(View.GONE);
            binding.cardQrCode.setVisibility(View.GONE);
        }

        // Cancel order button visibility
        boolean canCancel = PrintOrder.STATUS_PENDING.equals(order.getOrderStatus()) ||
                PrintOrder.STATUS_ACCEPTED.equals(order.getOrderStatus());
        binding.btnCancelOrder.setVisibility(canCancel ? View.VISIBLE : View.GONE);
    }

    private void showOnlinePaymentDialog() {
        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
        startActivity(intent);
    }

    private void showCancelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cancel Print Order");
        builder.setMessage("Are you sure you want to cancel this order? Subject to campus shop cancellation policy.");

        final EditText input = new EditText(this);
        input.setHint("Reason for cancellation (optional)");
        builder.setView(input);

        builder.setPositiveButton("Cancel Order", (dialog, which) -> {
            String reason = input.getText().toString().trim();
            binding.progressBar.setVisibility(View.VISIBLE);
            OrderRepository.getInstance().cancelOrder(orderId, reason.isEmpty() ? "Customer requested cancellation" : reason, task -> {
                binding.progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Order cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to cancel order", Toast.LENGTH_SHORT).show();
                }
            });
        });
        builder.setNegativeButton("Keep Order", null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (orderListener != null) {
            orderListener.remove();
        }
    }
}
