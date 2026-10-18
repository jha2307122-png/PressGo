package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.pressgo.app.databinding.ActivityOrderConfirmationBinding;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.models.Payment;
import com.pressgo.app.models.PriceBreakdown;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.SelectedDocument;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.PaymentRepository;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.FileDownloadUtils;
import com.pressgo.app.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

public class OrderConfirmationActivity extends AppCompatActivity {
    private static final String TAG = "OrderConfirmation";

    private ActivityOrderConfirmationBinding binding;
    private PrintOrder order;
    private ArrayList<SelectedDocument> documents;
    private PriceBreakdown breakdown;
    private String shopName;
    private boolean isSubmitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityOrderConfirmationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        order = (PrintOrder) getIntent().getSerializableExtra("order");
        documents = getIntent().getParcelableArrayListExtra("documents");
        if (documents == null) {
            try {
                documents = (ArrayList<SelectedDocument>) getIntent().getSerializableExtra("documents");
            } catch (Exception ignored) {}
        }
        breakdown = (PriceBreakdown) getIntent().getSerializableExtra("breakdown");
        shopName = getIntent().getStringExtra("shopName");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Confirm Order");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        populateSummary();
        setupListeners();
    }

    private void populateSummary() {
        if (order == null) return;

        binding.tvShopName.setText(shopName != null ? shopName : "Campus Printing Shop");

        String printMode = (order.isColor() ? "Color" : "B&W") + " • " + order.getPaperSize() + " • " +
                (order.isBothSides() ? "Double-sided" : "Single-sided") + " • " +
                (order.isPortrait() ? "Portrait" : "Landscape");
        binding.tvPrintSettingsSummary.setText(printMode);

        binding.tvCopiesSummary.setText(String.format(Locale.US, "Copies: %d", order.getCopies()));
        binding.tvPageRangeSummary.setText(String.format(Locale.US, "Page Range: %s (%d pages)", order.getPageRange(), order.getTotalPages()));

        StringBuilder finishing = new StringBuilder();
        if (order.isHasBinding()) finishing.append("Binding  ");
        if (order.isHasStapling()) finishing.append("Stapling  ");
        if (order.isPriority()) finishing.append("Priority Rush  ");
        if (finishing.length() == 0) finishing.append("None");
        binding.tvFinishingSummary.setText("Finishing: " + finishing.toString());

        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            binding.etConfirmCustomerNotes.setText(order.getCustomerNotes());
            binding.tvNotesSummary.setVisibility(View.VISIBLE);
            binding.tvNotesSummary.setText("Notes: " + order.getCustomerNotes());
        } else {
            binding.tvNotesSummary.setVisibility(View.GONE);
        }

        binding.tvTotalAmount.setText(String.format(Locale.US, "BDT %.2f", order.getEstimatedPrice()));

        if (documents != null) {
            StringBuilder filesSb = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                filesSb.append(i + 1).append(". ").append(documents.get(i).getFileName()).append("\n");
            }
            binding.tvFilesList.setText(filesSb.toString().trim());
        }
    }

    private void setupListeners() {
        binding.btnPlaceOrder.setOnClickListener(v -> submitOrder());
    }

    private void submitOrder() {
        if (isSubmitting) return;
        isSubmitting = true;

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnPlaceOrder.setEnabled(false);

        SessionManager sessionManager = new SessionManager(this);
        String resolvedUid;
        if (UserRepository.getInstance().getCurrentUser() != null) {
            resolvedUid = UserRepository.getInstance().getCurrentUser().getUid();
        } else if (sessionManager.getUserId() != null && !sessionManager.getUserId().trim().isEmpty()) {
            resolvedUid = sessionManager.getUserId();
        } else {
            resolvedUid = "demo_cust_uid";
        }
        final String currentUid = resolvedUid;
        // Save back into sessionManager so all subsequent screens recognize this customer
        sessionManager.saveUserSession(currentUid, Constants.ROLE_CUSTOMER, sessionManager.getShopId());

        String email = UserRepository.getInstance().getCurrentUser() != null
                ? UserRepository.getInstance().getCurrentUser().getEmail()
                : "";

        String name = UserRepository.getInstance().getCurrentUser() != null
                ? UserRepository.getInstance().getCurrentUser().getDisplayName()
                : "";
        if (name == null || name.trim().isEmpty()) name = "Customer";

        String phone = UserRepository.getInstance().getCurrentUser() != null
                ? UserRepository.getInstance().getCurrentUser().getPhoneNumber()
                : "";

        String updatedNotes = binding.etConfirmCustomerNotes.getText() != null
                ? binding.etConfirmCustomerNotes.getText().toString().trim()
                : "";
        order.setCustomerNotes(updatedNotes);
        order.setCustomerId(currentUid);
        order.setCustomerEmail(email);
        order.setCustomerName(name);
        if (phone != null && !phone.trim().isEmpty()) {
            order.setCustomerPhone(phone);
        }
        order.setShopName(shopName);
        order.setOrderStatus(PrintOrder.STATUS_PENDING);
        order.setPaymentStatus(PrintOrder.PAYMENT_UNPAID);
        order.setSubmittedAt(Timestamp.now());
        order.setCreatedAt(Timestamp.now());

        // Verify shop is open before placing order
        ShopRepository.getInstance().getShopById(order.getShopId(), shop -> {
            if (shop != null && !shop.isOpen()) {
                isSubmitting = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.btnPlaceOrder.setEnabled(true);
                binding.btnPlaceOrder.setText("Place Order");
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Shop is Currently Closed")
                        .setMessage("\"" + shop.getName() + "\" is currently closed and not accepting print jobs.\n\nPlease choose an open shop or try again later.")
                        .setPositiveButton("Choose Another Shop", (d, w) -> {
                            Intent intent = new Intent(this, ShopListActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Cancel", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                return;
            }
            proceedUploadAndOrderCreation(currentUid);
        }, err -> {
            proceedUploadAndOrderCreation(currentUid);
        });
    }

    private void proceedUploadAndOrderCreation(String currentUid) {
        String orderId = order.getId();
        if (orderId == null || orderId.isEmpty()) {
            orderId = OrderRepository.getInstance().generateOrderId();
            order.setId(orderId);
            order.setOrderId(orderId);
        }
        final String finalOrderId = orderId;

        binding.btnPlaceOrder.setText("Uploading documents...");

        Executors.newSingleThreadExecutor().execute(() -> {
            List<OrderFile> preparedFiles = new ArrayList<>();

            if (documents != null && !documents.isEmpty()) {
                int totalDocs = documents.size();
                for (int i = 0; i < totalDocs; i++) {
                    SelectedDocument doc = documents.get(i);
                    final int docIndex = i + 1;
                    runOnUiThread(() -> binding.btnPlaceOrder.setText("Uploading document " + docIndex + " of " + totalDocs + "..."));

                    OrderFile of = new OrderFile();
                    of.setId(UUID.randomUUID().toString());
                    of.setOrderId(finalOrderId);
                    of.setFileName(doc.getFileName());
                    of.setFileType(doc.getFileType());
                    of.setFileSizeBytes(doc.getFileSizeBytes());
                    of.setPageCount(doc.getPageCount());

                    if (doc.getUri() != null) {
                        of.setContentUri(doc.getUri().toString());

                        // 1. Cache copy locally on device
                        try {
                            File orderDir = new File(getExternalFilesDir(null), "orders/" + finalOrderId);
                            if (!orderDir.exists()) orderDir.mkdirs();
                            File localFile = new File(orderDir, doc.getFileName());

                            try (InputStream is = getContentResolver().openInputStream(doc.getUri());
                                 FileOutputStream fos = new FileOutputStream(localFile)) {
                                if (is != null) {
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = is.read(buf)) > 0) {
                                        fos.write(buf, 0, len);
                                    }
                                    fos.flush();
                                }
                            }

                            if (localFile.exists() && localFile.length() > 0) {
                                of.setLocalPath(localFile.getAbsolutePath());
                                of.setLocalUri(localFile.getAbsolutePath());
                                of.setFileSizeBytes(localFile.length());

                                // 2. If file is small (< 750 KB), store Base64 backup directly in Firestore
                                if (localFile.length() < 750 * 1024) {
                                    try (FileInputStream fis = new FileInputStream(localFile)) {
                                        byte[] bytes = new byte[(int) localFile.length()];
                                        int read = fis.read(bytes);
                                        if (read > 0) {
                                            of.setBase64Data(Base64.encodeToString(bytes, Base64.NO_WRAP));
                                        }
                                    } catch (Exception b64Ex) {
                                        Log.w(TAG, "Base64 encoding skipped", b64Ex);
                                    }
                                }

                                // 3. Upload to Firebase Storage
                                try {
                                    String storagePath = "orders/" + finalOrderId + "/files/" + UUID.randomUUID().toString() + "_" + doc.getFileName();
                                    StorageReference fileRef = FileDownloadUtils.getFirebaseStorage().getReference().child(storagePath);
                                    UploadTask.TaskSnapshot snap = Tasks.await(fileRef.putFile(Uri.fromFile(localFile)));
                                    Uri downloadUri = Tasks.await(fileRef.getDownloadUrl());

                                    of.setStoragePath(storagePath);
                                    of.setDownloadUrl(downloadUri.toString());
                                    of.setUploaded(true);
                                    of.setUploadedAt(Timestamp.now());
                                    Log.d(TAG, "Successfully uploaded document to cloud: " + downloadUri);
                                } catch (Exception storageEx) {
                                    Log.e(TAG, "Firebase Storage upload error (local copy & base64 retained): " + storageEx.getMessage(), storageEx);
                                }
                            }
                        } catch (Exception fileEx) {
                            Log.e(TAG, "Error caching and uploading document: " + doc.getFileName(), fileEx);
                        }
                    }

                    preparedFiles.add(of);
                }
            }

            // Create order in Firestore on UI thread
            runOnUiThread(() -> {
                binding.btnPlaceOrder.setText("Finalizing order...");
                OrderRepository.getInstance().createOrder(order, preparedFiles, createdOrderId -> {
                    // Create payment record
                    Payment payment = new Payment();
                    payment.setOrderId(createdOrderId);
                    payment.setCustomerId(currentUid);
                    payment.setShopId(order.getShopId());
                    payment.setAmount(order.getEstimatedPrice());
                    String method = binding.rbCashOnPickup.isChecked() ? Payment.METHOD_CASH : Payment.METHOD_ONLINE;
                    payment.setMethod(method);
                    payment.setStatus(Payment.STATUS_UNPAID);
                    PaymentRepository.getInstance().createPayment(payment, task -> {});

                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show();

                    // Generate order placed notification
                    com.pressgo.app.models.Notification placedNotif = new com.pressgo.app.models.Notification(
                            UUID.randomUUID().toString(),
                            currentUid,
                            createdOrderId,
                            "Order Placed Successfully! 📄",
                            "Your order #" + createdOrderId + " has been sent to " + (order.getShopName() != null ? order.getShopName() : "the print shop") + ".",
                            com.pressgo.app.models.Notification.TYPE_GENERAL,
                            false,
                            Timestamp.now(),
                            null
                    );
                    com.pressgo.app.repositories.NotificationRepository.getInstance().createNotification(placedNotif, null);
                    com.pressgo.app.utils.NotificationUtils.createOrderNotification(
                            this,
                            "Order Placed Successfully! 📄",
                            "Your order #" + createdOrderId + " has been sent to the print shop.",
                            createdOrderId
                    );

                    Intent intent = new Intent(this, OrderTrackingActivity.class);
                    intent.putExtra(Constants.EXTRA_ORDER_ID, createdOrderId);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }, e -> {
                    isSubmitting = false;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnPlaceOrder.setEnabled(true);
                    binding.btnPlaceOrder.setText("Place Order");
                    Log.e(TAG, "Error placing order", e);
                    Toast.makeText(this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
