package com.pressgo.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pressgo.app.R;
import com.pressgo.app.databinding.ActivityPaymentBinding;
import com.pressgo.app.models.Payment;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.Shop;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.PaymentRepository;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.QRCodeUtils;

import java.util.Locale;
import java.util.UUID;

public class PaymentActivity extends AppCompatActivity {
    private static final String TAG = "PaymentActivity";

    private ActivityPaymentBinding binding;
    private String orderId;
    private PrintOrder order;
    private Shop shop;
    private String merchantNumber = "01700-123456";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderId = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        if (orderId == null || orderId.isEmpty()) {
            orderId = getIntent().getStringExtra("orderId");
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Payment & Settlement");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
        loadOrderData();
    }

    private void loadOrderData() {
        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Order ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        OrderRepository.getInstance().getOrderById(orderId, o -> {
            order = o;
            binding.tvOrderId.setText("Order #" + order.getId());
            binding.tvShopName.setText(order.getShopName() != null ? order.getShopName() : "Campus Print Shop");
            String amtFormatted = String.format(Locale.US, "BDT %.2f", order.getEstimatedPrice());
            binding.tvAmountDue.setText(amtFormatted);
            binding.tvPaymentAmount.setText(amtFormatted);

            boolean isPaid = PrintOrder.PAYMENT_PAID.equalsIgnoreCase(order.getPaymentStatus());
            if (isPaid) {
                binding.progressBar.setVisibility(View.GONE);
                showAlreadyPaidUI();
            } else {
                binding.tvPaymentStatus.setText("UNPAID");
                binding.tvPaymentStatus.setBackgroundResource(R.drawable.bg_badge_yellow);
                binding.tvPaymentStatus.setTextColor(0xFFFFFFFF);

                // Fetch shop details for real merchant number
                if (order.getShopId() != null && !order.getShopId().isEmpty()) {
                    ShopRepository.getInstance().getShopById(order.getShopId(), s -> {
                        binding.progressBar.setVisibility(View.GONE);
                        shop = s;
                        if (shop != null && shop.getPhone() != null && !shop.getPhone().trim().isEmpty()) {
                            merchantNumber = shop.getPhone().trim();
                        }
                        updatePaymentUIForSelectedMethod();
                    }, err -> {
                        binding.progressBar.setVisibility(View.GONE);
                        updatePaymentUIForSelectedMethod();
                    });
                } else {
                    binding.progressBar.setVisibility(View.GONE);
                    updatePaymentUIForSelectedMethod();
                }
            }
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load order info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setupListeners() {
        binding.rgPaymentMethods.setOnCheckedChangeListener((group, checkedId) -> {
            updatePaymentUIForSelectedMethod();
        });

        binding.btnCopyNumber.setOnClickListener(v -> {
            copyToClipboard("Merchant Number", merchantNumber);
        });

        binding.btnCopyAmount.setOnClickListener(v -> {
            if (order != null) {
                copyToClipboard("Amount", String.format(Locale.US, "%.2f", order.getEstimatedPrice()));
            }
        });

        binding.btnPayNow.setOnClickListener(v -> handlePaymentSubmit());

        binding.btnViewReceipt.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReceiptActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });
    }

    private void updatePaymentUIForSelectedMethod() {
        if (binding.rbCashOnPickup.isChecked()) {
            binding.cardOnlinePayment.setVisibility(View.GONE);
            binding.cardCashInfo.setVisibility(View.VISIBLE);
            binding.btnPayNow.setText("Confirm Cash on Pickup");
            return;
        }

        binding.cardOnlinePayment.setVisibility(View.VISIBLE);
        binding.cardCashInfo.setVisibility(View.GONE);

        String channelName = "bKash";
        String channelPrefix = "bkash";
        if (binding.rbNagad.isChecked()) {
            channelName = "Nagad";
            channelPrefix = "nagad";
        } else if (binding.rbRocket.isChecked()) {
            channelName = "Rocket / Upay";
            channelPrefix = "rocket";
        }

        binding.tvPaymentChannelTitle.setText("Scan to Pay via " + channelName);
        binding.tvMerchantLabel.setText("Shop " + channelName + " Number:");
        binding.tvMerchantNumber.setText(merchantNumber);
        binding.btnPayNow.setText("Submit " + channelName + " TrxID & Verify");

        // Generate dynamic Payment QR Code
        generateAndShowPaymentQr(channelPrefix, channelName);
    }

    private void generateAndShowPaymentQr(String channelPrefix, String channelName) {
        if (order == null) return;
        double amount = order.getEstimatedPrice();
        String currentOrderId = order.getId() != null ? order.getId() : orderId;

        // Structured URI that mobile banking / QR scanners can parse
        String qrPayload = channelPrefix + "://payment?number=" + merchantNumber +
                "&amount=" + String.format(Locale.US, "%.2f", amount) +
                "&ref=" + currentOrderId;

        Bitmap qrBitmap = QRCodeUtils.generateQRCode(qrPayload, 300, 300);
        if (qrBitmap != null) {
            binding.ivPaymentQr.setImageBitmap(qrBitmap);
        }
    }

    private void handlePaymentSubmit() {
        if (order == null) return;

        // Cash on Pickup
        if (binding.rbCashOnPickup.isChecked()) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnPayNow.setEnabled(false);

            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setCustomerId(order.getCustomerId());
            payment.setShopId(order.getShopId());
            payment.setAmount(order.getEstimatedPrice());
            payment.setMethod(Payment.METHOD_CASH);
            payment.setStatus(Payment.STATUS_UNPAID);

            PaymentRepository.getInstance().createPayment(payment, task -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnPayNow.setEnabled(true);
                Toast.makeText(this, "Order marked for Cash on Pickup. Pay at shop counter.", Toast.LENGTH_LONG).show();
                finish();
            });
            return;
        }

        // Online Payment via bKash / Nagad / Rocket
        String selectedMethod = Payment.METHOD_BKASH;
        if (binding.rbNagad.isChecked()) {
            selectedMethod = Payment.METHOD_NAGAD;
        } else if (binding.rbRocket.isChecked()) {
            selectedMethod = Payment.METHOD_WALLET;
        }

        String trxId = binding.etTransactionId.getText() != null
                ? binding.etTransactionId.getText().toString().trim()
                : "";

        if (trxId.isEmpty()) {
            binding.tilTransactionId.setError("Please enter the Transaction ID (TrxID) from your SMS or app");
            binding.etTransactionId.requestFocus();
            return;
        }

        if (trxId.length() < 4) {
            binding.tilTransactionId.setError("Transaction ID is too short (min 4 characters)");
            binding.etTransactionId.requestFocus();
            return;
        }

        binding.tilTransactionId.setError(null);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnPayNow.setEnabled(false);

        final String finalMethod = selectedMethod;
        final String finalTrxId = trxId;

        PaymentRepository.getInstance().markOrderPaid(orderId, finalMethod, finalTrxId, task -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnPayNow.setEnabled(true);

            if (task.isSuccessful()) {
                Toast.makeText(this, "🎉 Payment Verified! Your Pickup QR code is now ready.", Toast.LENGTH_LONG).show();
                order.setPaymentStatus(PrintOrder.PAYMENT_PAID);
                order.setTransactionRef(finalTrxId);
                order.setPaymentMethod(finalMethod);
                showPaymentSuccessUI(finalTrxId);
            } else {
                Toast.makeText(this, "Payment submission failed. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPaymentSuccessUI(String trxId) {
        binding.layoutPaymentForm.setVisibility(View.GONE);
        binding.cardPaymentSuccess.setVisibility(View.VISIBLE);

        binding.tvPaymentStatus.setText("PAID");
        binding.tvPaymentStatus.setBackgroundResource(R.drawable.bg_badge_green);
        binding.tvPaymentStatus.setTextColor(0xFFFFFFFF);

        binding.tvSuccessTrxId.setText("Transaction ID (TrxID): " + trxId);

        // Generate customer's Pickup QR Code
        String pickupToken = order.getPickupToken();
        if (pickupToken == null || pickupToken.isEmpty()) {
            pickupToken = UUID.randomUUID().toString();
        }
        String tokenPayload = order.getId() + ":" + pickupToken;
        Bitmap pickupQr = QRCodeUtils.generateQRCode(tokenPayload, 260, 260);
        if (pickupQr != null) {
            binding.ivPickupQr.setImageBitmap(pickupQr);
        }
    }

    private void showAlreadyPaidUI() {
        binding.layoutPaymentForm.setVisibility(View.GONE);
        binding.cardPaymentSuccess.setVisibility(View.VISIBLE);

        binding.tvPaymentStatus.setText("PAID");
        binding.tvPaymentStatus.setBackgroundResource(R.drawable.bg_badge_green);
        binding.tvPaymentStatus.setTextColor(0xFFFFFFFF);

        String trx = order.getTransactionRef() != null && !order.getTransactionRef().isEmpty()
                ? order.getTransactionRef()
                : "VERIFIED_ONLINE";
        binding.tvSuccessTrxId.setText("Transaction ID (TrxID): " + trx);

        String pickupToken = order.getPickupToken();
        if (pickupToken == null || pickupToken.isEmpty()) {
            pickupToken = UUID.randomUUID().toString();
        }
        String tokenPayload = order.getId() + ":" + pickupToken;
        Bitmap pickupQr = QRCodeUtils.generateQRCode(tokenPayload, 260, 260);
        if (pickupQr != null) {
            binding.ivPickupQr.setImageBitmap(pickupQr);
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied " + label + ": " + text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
