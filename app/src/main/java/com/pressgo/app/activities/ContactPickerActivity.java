package com.pressgo.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.pressgo.app.adapters.ContactAdapter;
import com.pressgo.app.databinding.ActivityContactPickerBinding;
import com.pressgo.app.databinding.DialogPickupAuthorizationConfirmationBinding;
import com.pressgo.app.databinding.DialogSharePickupOptionsBinding;
import com.pressgo.app.models.ContactItem;
import com.pressgo.app.models.Notification;
import com.pressgo.app.models.PickupAuthorization;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.NotificationRepository;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.PickupAuthorizationRepository;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.ImplicitIntentUtils;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for accessing device contacts via Android's Contacts Content Provider
 * and generating contact-based order pickup authorizations for PressGo.
 */
public class ContactPickerActivity extends AppCompatActivity {

    private static final String TAG = "ContactPickerActivity";
    public static final String EXTRA_SELECTED_CONTACT_NAME = "extra_selected_contact_name";
    public static final String EXTRA_SELECTED_CONTACT_PHONE = "extra_selected_contact_phone";

    private ActivityContactPickerBinding binding;
    private ContactAdapter adapter;
    private final List<ContactItem> contactList = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private SessionManager sessionManager;
    private String orderIdParam;
    private PrintOrder currentOrder;
    private final List<PrintOrder> eligibleOrdersList = new ArrayList<>();
    private String customerUid;
    private String customerName = "PressGo Customer";
    private String customerPhone = "";

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    binding.cardPermissionDenied.setVisibility(View.GONE);
                    loadContacts();
                } else {
                    handlePermissionDenied();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContactPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Campus Contacts & Sharing");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        orderIdParam = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);

        resolveCustomerInfo();
        setupRecyclerView();
        setupSearch();
        setupActionButtons();
        retrieveEligibleOrders();

        checkAndRequestContactsPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If permission was granted in Settings while the app was backgrounded
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            if (binding.cardPermissionDenied.getVisibility() == View.VISIBLE || contactList.isEmpty()) {
                binding.cardPermissionDenied.setVisibility(View.GONE);
                loadContacts();
            }
        }
    }

    private void resolveCustomerInfo() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            customerUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null &&
                    !FirebaseAuth.getInstance().getCurrentUser().getDisplayName().trim().isEmpty()) {
                customerName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName().trim();
            }
            if (FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() != null) {
                customerPhone = FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber().trim();
            }
        } else if (sessionManager.getUserId() != null && !sessionManager.getUserId().trim().isEmpty()) {
            customerUid = sessionManager.getUserId();
        } else {
            customerUid = "demo_cust_uid";
        }

        // Fetch detailed profile for updated name
        UserRepository.getInstance().getCurrentUserProfile(user -> {
            if (user != null) {
                if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                    customerName = user.getDisplayName().trim();
                }
                if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
                    customerPhone = user.getPhone().trim();
                }
            }
        }, null);
    }

    /**
     * Retrieves eligible active orders for the current customer to associate with pickup authorization.
     */
    private void retrieveEligibleOrders() {
        if (orderIdParam != null && !orderIdParam.trim().isEmpty()) {
            OrderRepository.getInstance().getOrderById(orderIdParam.trim(), order -> {
                if (order != null) {
                    currentOrder = order;
                    eligibleOrdersList.clear();
                    eligibleOrdersList.add(order);
                    updateActiveOrderUI();
                } else {
                    fetchCustomerActiveOrders();
                }
            }, e -> fetchCustomerActiveOrders());
        } else {
            fetchCustomerActiveOrders();
        }
    }

    private void fetchCustomerActiveOrders() {
        String email = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail()
                : null;

        OrderRepository.getInstance().getCustomerOrders(customerUid, email, orders -> {
            eligibleOrdersList.clear();
            if (orders != null) {
                for (PrintOrder order : orders) {
                    String status = order.getOrderStatus();
                    // Orders eligible for pickup authorization are active (pending, accepted, printing, or ready)
                    if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(status) ||
                            PrintOrder.STATUS_PRINTING.equals(status) ||
                            PrintOrder.STATUS_ACCEPTED.equals(status) ||
                            PrintOrder.STATUS_PENDING.equals(status)) {
                        eligibleOrdersList.add(order);
                    }
                }
            }

            if (!eligibleOrdersList.isEmpty()) {
                currentOrder = eligibleOrdersList.get(0);
            } else {
                currentOrder = null;
            }
            updateActiveOrderUI();
        }, e -> {
            Log.e(TAG, "Failed retrieving active orders", e);
            currentOrder = null;
            updateActiveOrderUI();
        });
    }

    private void updateActiveOrderUI() {
        if (currentOrder != null) {
            binding.cardNoActiveOrder.setVisibility(View.GONE);
            binding.cardActiveOrder.setVisibility(View.VISIBLE);
            binding.tvActiveOrderTitle.setText("Active Order: #" + currentOrder.getId());
            String shop = currentOrder.getShopName() != null ? currentOrder.getShopName() : "Campus Shop";
            String status = currentOrder.getOrderStatus() != null ? currentOrder.getOrderStatus() : "Active";
            binding.tvActiveOrderSubtitle.setText(shop + " • " + status);

            if (eligibleOrdersList.size() > 1) {
                binding.btnChangeOrder.setVisibility(View.VISIBLE);
                binding.btnChangeOrder.setOnClickListener(v -> showOrderSelectionDialog());
            } else {
                binding.btnChangeOrder.setVisibility(View.GONE);
            }
        } else {
            binding.cardActiveOrder.setVisibility(View.GONE);
            binding.cardNoActiveOrder.setVisibility(View.VISIBLE);
            binding.btnPlaceOrderPrompt.setOnClickListener(v -> {
                startActivity(new Intent(this, ShopListActivity.class));
            });
        }
    }

    private void showOrderSelectionDialog() {
        if (eligibleOrdersList.isEmpty()) return;

        String[] orderLabels = new String[eligibleOrdersList.size()];
        for (int i = 0; i < eligibleOrdersList.size(); i++) {
            PrintOrder o = eligibleOrdersList.get(i);
            String shop = o.getShopName() != null ? o.getShopName() : "Shop";
            orderLabels[i] = "#" + o.getId() + " (" + o.getOrderStatus() + " - " + shop + ")";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Order for Authorization")
                .setItems(orderLabels, (dialog, which) -> {
                    currentOrder = eligibleOrdersList.get(which);
                    updateActiveOrderUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(contactList, this::onContactClicked);
        binding.rvContacts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvContacts.setAdapter(adapter);
    }

    private void onContactClicked(ContactItem contact) {
        if (contact == null) return;

        // If no eligible order exists, explain that customer must first place an order
        if (currentOrder == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No Active Order Found")
                    .setMessage("You currently have no active print orders to authorize for pickup.\n\nPlease place an order first before sharing pickup authorization with a friend.")
                    .setPositiveButton("Place an Order", (dialog, which) -> {
                        startActivity(new Intent(this, ShopListActivity.class));
                    })
                    .setNegativeButton("Dismiss", null)
                    .show();
            return;
        }

        // Show confirmation dialog containing contact details, order information, and "Share Pickup Authorization" button
        showAuthorizationConfirmationDialog(contact, currentOrder);
    }

    /**
     * Displays a confirmation dialog containing:
     * - Selected contact name
     * - Selected contact phone number
     * - Current order ID
     * - Order pickup information
     * - Button labeled "Share Pickup Authorization"
     */
    private void showAuthorizationConfirmationDialog(ContactItem contact, PrintOrder order) {
        DialogPickupAuthorizationConfirmationBinding dialogBinding =
                DialogPickupAuthorizationConfirmationBinding.inflate(LayoutInflater.from(this));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        // Populate dialog fields
        dialogBinding.tvDialogContactName.setText(contact.getName());
        dialogBinding.tvDialogContactPhone.setText(
                contact.hasPhoneNumber() ? contact.getPhoneNumber() : "No phone number available"
        );

        dialogBinding.tvDialogOrderId.setText("Order ID: " + order.getId());
        String shopName = order.getShopName() != null ? order.getShopName() : "PressGo Campus Printing Shop";
        dialogBinding.tvDialogShopName.setText("Pickup Shop: " + shopName);

        String printMode = (order.isColor() ? "Color" : "B&W") + " • " +
                order.getTotalPages() + " pages (" + order.getCopies() + " copies) • " +
                order.getOrderStatus();
        dialogBinding.tvDialogOrderSummary.setText("Specs: " + printMode);

        String payment = "Amount: " + Constants.CURRENCY_SYMBOL +
                String.format(Locale.US, "%.2f", order.getEstimatedPrice()) + " (" + order.getPaymentStatus() + ")";
        dialogBinding.tvDialogOrderAmount.setText(payment);

        dialogBinding.btnCancelAuthorization.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnShareAuthorization.setOnClickListener(v -> {
            dialogBinding.progressBarDialog.setVisibility(View.VISIBLE);
            dialogBinding.btnShareAuthorization.setEnabled(false);

            // Generate authorization message
            String authMessage = ImplicitIntentUtils.buildPickupAuthorizationMessage(
                    contact.getName(),
                    customerName,
                    order.getId(),
                    order.getShopName()
            );

            // Construct PickupAuthorization model
            PickupAuthorization auth = new PickupAuthorization();
            auth.setOrderId(order.getId());
            auth.setCustomerUid(customerUid);
            auth.setCustomerName(customerName);
            auth.setCustomerPhone(customerPhone);
            auth.setAuthorizedContactName(contact.getName());
            auth.setAuthorizedContactPhone(contact.hasPhoneNumber() ? contact.getPhoneNumber() : "");
            auth.setAuthorizationMessage(authMessage);
            auth.setStatus(PickupAuthorization.STATUS_PENDING_VERIFICATION);
            auth.setShopId(order.getShopId());
            auth.setShopName(order.getShopName());

            // Save authorization record in Firestore
            PickupAuthorizationRepository.getInstance().createOrUpdateAuthorization(auth, authId -> {
                dialogBinding.progressBarDialog.setVisibility(View.GONE);
                dialog.dismiss();

                // Save in-app notification for the customer
                createCustomerAuthorizationNotification(order.getId(), contact.getName());

                // Set Activity result for backward compatibility
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SELECTED_CONTACT_NAME, contact.getName());
                resultIntent.putExtra(EXTRA_SELECTED_CONTACT_PHONE, contact.getPhoneNumber());
                resultIntent.putExtra(Constants.EXTRA_ORDER_ID, order.getId());
                resultIntent.putExtra(Constants.EXTRA_AUTHORIZATION_ID, authId);
                setResult(RESULT_OK, resultIntent);

                // Show sharing options dialog (WhatsApp, SMS, Other Apps)
                showSharingOptionsDialog(contact, order.getId(), authMessage);

            }, e -> {
                dialogBinding.progressBarDialog.setVisibility(View.GONE);
                dialogBinding.btnShareAuthorization.setEnabled(true);
                Log.e(TAG, "Error creating pickup authorization", e);
                Toast.makeText(this, "Failed to save authorization: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        });

        dialog.show();
    }

    /**
     * Shows options to share the generated authorization message:
     * - Option A: WhatsApp Sharing (ACTION_SEND, text/plain)
     * - Option B: SMS Sharing (ACTION_SENDTO, sms: URI)
     * - Option C: Other Apps (Android native Sharesheet)
     */
    private void showSharingOptionsDialog(ContactItem contact, String orderId, String authorizationMessage) {
        DialogSharePickupOptionsBinding shareBinding =
                DialogSharePickupOptionsBinding.inflate(LayoutInflater.from(this));

        AlertDialog shareDialog = new MaterialAlertDialogBuilder(this)
                .setView(shareBinding.getRoot())
                .setCancelable(true)
                .create();

        shareBinding.tvShareSubtitle.setText("Authorized: " + contact.getName() + " for Order #" + orderId);
        shareBinding.tvMessagePreview.setText(authorizationMessage);

        // Option A: WhatsApp Sharing
        shareBinding.btnShareWhatsApp.setOnClickListener(v -> {
            ImplicitIntentUtils.shareViaWhatsApp(this, authorizationMessage);
        });

        // Option B: SMS Sharing
        shareBinding.btnShareSms.setOnClickListener(v -> {
            if (contact.hasPhoneNumber()) {
                ImplicitIntentUtils.shareViaSms(this, contact.getPhoneNumber(), authorizationMessage);
            } else {
                Toast.makeText(this, "Selected contact has no phone number. Choose WhatsApp or Sharesheet.", Toast.LENGTH_SHORT).show();
                ImplicitIntentUtils.sharePlainText(this, "Share Authorization", authorizationMessage);
            }
        });

        // Option C: Other Apps (Android Native Sharesheet)
        shareBinding.btnShareOther.setOnClickListener(v -> {
            ImplicitIntentUtils.sharePlainText(this, "Share Pickup Authorization via", authorizationMessage);
        });

        shareBinding.btnShareDone.setOnClickListener(v -> {
            shareDialog.dismiss();
            Toast.makeText(this, "Pickup authorization shared successfully!", Toast.LENGTH_SHORT).show();
        });

        shareDialog.show();
    }

    private void createCustomerAuthorizationNotification(String orderId, String contactName) {
        Notification notif = new Notification(
                UUID.randomUUID().toString(),
                customerUid,
                orderId,
                "Pickup Authorization Created",
                "You authorized " + contactName + " to collect Order #" + orderId + ". They must present campus ID at pickup.",
                Notification.TYPE_GENERAL,
                false,
                Timestamp.now(),
                null
        );
        NotificationRepository.getInstance().createNotification(notif, null);
    }

    private void setupSearch() {
        binding.etSearchContacts.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.filter(s != null ? s.toString() : "");
                    updateEmptyState(adapter.getItemCount() == 0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupActionButtons() {
        binding.btnGrantPermission.setOnClickListener(v -> checkAndRequestContactsPermission());
        binding.btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });
    }

    private void checkAndRequestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            binding.cardPermissionDenied.setVisibility(View.GONE);
            loadContacts();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed for Contacts")
                    .setMessage("PressGo requests contact access only so you can choose a campus friend to share pickup authorization. Your contacts remain on your device and are never uploaded.")
                    .setPositiveButton("Allow Access", (dialog, which) -> {
                        requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
                    })
                    .setNegativeButton("Not Now", (dialog, which) -> {
                        handlePermissionDenied();
                    })
                    .show();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void handlePermissionDenied() {
        binding.progressBar.setVisibility(View.GONE);
        binding.cardPermissionDenied.setVisibility(View.VISIBLE);
        binding.tvEmptyContacts.setVisibility(View.GONE);
        binding.rvContacts.setVisibility(View.GONE);
        binding.tilSearchContacts.setVisibility(View.GONE);
        Toast.makeText(this, "Contacts permission was denied. Cannot load campus contacts.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Reads contacts from Android Contacts Provider using ContentResolver.
     * Loads contacts with phone numbers as well as contacts without phone numbers,
     * extracts contact profile picture URIs when available, and performs deduplication.
     */
    private void loadContacts() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.cardPermissionDenied.setVisibility(View.GONE);
        binding.tilSearchContacts.setVisibility(View.VISIBLE);
        binding.rvContacts.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            List<ContactItem> results = new ArrayList<>();
            Set<String> uniqueKeys = new HashSet<>();
            Set<Long> seenContactIds = new HashSet<>();

            // 1. Query contacts with phone numbers
            String[] phoneProjection = new String[]{
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            };

            Cursor phoneCursor = null;
            try {
                phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        phoneProjection,
                        null,
                        null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
                );

                if (phoneCursor != null) {
                    int idCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                    int nameCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY);
                    int phoneCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int photoCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI);

                    while (phoneCursor.moveToNext()) {
                        long contactId = idCol >= 0 ? phoneCursor.getLong(idCol) : 0;
                        String name = nameCol >= 0 ? phoneCursor.getString(nameCol) : "Unknown";
                        String phone = phoneCol >= 0 ? phoneCursor.getString(phoneCol) : "";
                        String photo = photoCol >= 0 ? phoneCursor.getString(photoCol) : null;

                        String cleanPhone = phone != null ? phone.replaceAll("\\s+", "") : "";
                        String dedupeKey = (name != null ? name.trim().toLowerCase() : "") + "|" + cleanPhone;

                        if (!uniqueKeys.contains(dedupeKey)) {
                            uniqueKeys.add(dedupeKey);
                            seenContactIds.add(contactId);
                            results.add(new ContactItem(contactId, name, phone, photo));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying phone contacts", e);
            } finally {
                if (phoneCursor != null && !phoneCursor.isClosed()) {
                    phoneCursor.close();
                }
            }

            // 2. Query all contacts to capture contacts who DO NOT have phone numbers
            String[] contactsProjection = new String[]{
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI
            };

            Cursor contactsCursor = null;
            try {
                contactsCursor = getContentResolver().query(
                        ContactsContract.Contacts.CONTENT_URI,
                        contactsProjection,
                        null,
                        null,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
                );

                if (contactsCursor != null) {
                    int idCol = contactsCursor.getColumnIndex(ContactsContract.Contacts._ID);
                    int nameCol = contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                    int photoCol = contactsCursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI);

                    while (contactsCursor.moveToNext()) {
                        long contactId = idCol >= 0 ? contactsCursor.getLong(idCol) : 0;
                        if (!seenContactIds.contains(contactId)) {
                            String name = nameCol >= 0 ? contactsCursor.getString(nameCol) : "Unnamed Contact";
                            String photo = photoCol >= 0 ? contactsCursor.getString(photoCol) : null;
                            String dedupeKey = (name != null ? name.trim().toLowerCase() : "") + "|";

                            if (!uniqueKeys.contains(dedupeKey)) {
                                uniqueKeys.add(dedupeKey);
                                results.add(new ContactItem(contactId, name, "", photo));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying general contacts", e);
            } finally {
                if (contactsCursor != null && !contactsCursor.isClosed()) {
                    contactsCursor.close();
                }
            }

            // Sort results alphabetically by name
            Collections.sort(results, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                contactList.clear();
                contactList.addAll(results);
                if (adapter != null) {
                    adapter.setContacts(contactList);
                }
                updateEmptyState(contactList.isEmpty());
            });
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.tvEmptyContacts.setVisibility(View.VISIBLE);
            binding.rvContacts.setVisibility(View.GONE);
        } else {
            binding.tvEmptyContacts.setVisibility(View.GONE);
            binding.rvContacts.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
