package com.pressgo.app.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.pressgo.app.R;
import com.pressgo.app.adapters.AdminOrderAdapter;
import com.pressgo.app.databinding.ActivityAdminDashboardBinding;
import com.pressgo.app.models.InventoryItem;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.NotificationUtils;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.Printer;
import com.pressgo.app.models.User;
import com.pressgo.app.repositories.InventoryRepository;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.repositories.UserRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.google.firebase.Timestamp;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboardActivity";

    private ActivityAdminDashboardBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private com.pressgo.app.utils.SessionManager sessionManager;

    private String shopId;
    private String adminName;
    private String shopName;
    private double shopLat = 0.0;
    private double shopLng = 0.0;
    private String shopAddress = "";

    private final ActivityResultLauncher<Intent> locationPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    shopLat = result.getData().getDoubleExtra(ShopMapActivity.EXTRA_LATITUDE, shopLat);
                    shopLng = result.getData().getDoubleExtra(ShopMapActivity.EXTRA_LONGITUDE, shopLng);
                    updateLocationCardText();
                }
            });

    // Realtime listeners
    private ListenerRegistration ordersListener;
    private ListenerRegistration printersListener;
    private ListenerRegistration inventoryListener;
    private ListenerRegistration shopStatusListener;
    private boolean isShopOpen = true;

    // Repositories
    private OrderRepository orderRepository;
    private InventoryRepository inventoryRepository;
    private UserRepository userRepository;

    // Adapters
    private AdminOrderAdapter recentOrderAdapter;
    private List<PrintOrder> recentOrders = new ArrayList<>();
    private final java.util.Set<String> knownOrderIds = new java.util.HashSet<>();
    private boolean isFirstOrdersSnapshot = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NotificationUtils.createNotificationChannels(this);
        NotificationUtils.requestNotificationPermission(this);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        orderRepository = OrderRepository.getInstance();
        inventoryRepository = InventoryRepository.getInstance();
        userRepository = UserRepository.getInstance();
        sessionManager = new com.pressgo.app.utils.SessionManager(this);

        if (currentUser == null && !sessionManager.isLoggedIn()) {
            Log.w(TAG, "No authenticated user, redirecting to login");
            redirectToLogin();
            return;
        }

        if (currentUser == null) {
            shopId = sessionManager.getShopId();
            if (shopId == null || shopId.isEmpty()) shopId = "demo_shop_01";
            adminName = "Shopkeeper Admin";
            shopName = "Campus Print Hub";
            shopAddress = "Student Center 2nd Floor";
            setupUI();
            return;
        }

        verifyAdminRole();
    }

    // ─── Role Verification ────────────────────────────────────────────────────

    private void verifyAdminRole() {
        Log.d(TAG, "Verifying admin role for uid=" + currentUser.getUid());
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Log.w(TAG, "User document not found");
                        showError("User profile not found.");
                        finish();
                        return;
                    }
                    String role = doc.getString("role");
                    if (!"ADMIN".equals(role) && !"STAFF".equals(role)) {
                        Log.w(TAG, "Unauthorized role: " + role);
                        Toast.makeText(this, "Access denied. Admin/Staff only.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    adminName = doc.getString("displayName");
                    if (adminName == null) adminName = "Admin";
                    shopId = doc.getString("shopId");
                    if (shopId == null || shopId.trim().isEmpty()) {
                        shopId = sessionManager.getShopId();
                    }
                    if (shopId != null && !shopId.trim().isEmpty()) {
                        Log.d(TAG, "Admin verified. role=" + role + " shopId=" + shopId);
                        loadShopInfo();
                    } else {
                        db.collection("shops").whereEqualTo("ownerId", currentUser.getUid()).limit(1).get()
                                .addOnSuccessListener(shopSnaps -> {
                                    if (!shopSnaps.isEmpty()) {
                                        shopId = shopSnaps.getDocuments().get(0).getId();
                                        sessionManager.saveUserSession(currentUser.getUid(), role, shopId);
                                        db.collection("users").document(currentUser.getUid()).update("shopId", shopId);
                                        Log.d(TAG, "Resolved shopId=" + shopId + " by ownerId=" + currentUser.getUid());
                                        loadShopInfo();
                                    } else {
                                        Log.w(TAG, "No shop found for ownerId=" + currentUser.getUid());
                                        showError("No shop associated with this account.");
                                        finish();
                                    }
                                }).addOnFailureListener(err -> {
                                    Log.e(TAG, "Error looking up shop by ownerId", err);
                                    showError("No shop associated with this account.");
                                    finish();
                                });
                        return;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to verify role", e);
                    showError("Failed to verify permissions.");
                    finish();
                });
    }

    private void loadShopInfo() {
        db.collection("shops").document(shopId)
                .get()
                .addOnSuccessListener(doc -> {
                    shopName = doc.getString("name");
                    if (shopName == null) shopName = "My Shop";
                    Double lat = doc.getDouble("latitude");
                    Double lng = doc.getDouble("longitude");
                    if (lat != null) shopLat = lat;
                    if (lng != null) shopLng = lng;
                    shopAddress = doc.getString("address");
                    if (shopAddress == null) shopAddress = "";
                    Boolean open = doc.getBoolean("isOpen");
                    if (open == null) open = doc.getBoolean("open");
                    if (open != null) {
                        isShopOpen = open;
                    } else {
                        Boolean local = ShopRepository.getInstance().getLocalShopOpenOverride(shopId);
                        isShopOpen = local != null ? local : true;
                    }
                    Log.d(TAG, "Shop loaded: " + shopName + " coords: " + shopLat + ", " + shopLng + " isOpen=" + isShopOpen);
                    setupUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load shop", e);
                    shopName = "My Shop";
                    Boolean local = ShopRepository.getInstance().getLocalShopOpenOverride(shopId);
                    isShopOpen = local != null ? local : true;
                    setupUI();
                });
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private void setupUI() {
        Log.d(TAG, "setupUI");
        binding.textWelcome.setText("Welcome, " + adminName);
        binding.textShopName.setText(shopName);

        setupShopStatusCard();
        setupLocationCard();
        setupOrderSummaryCardClicks();
        setupBottomNavigation();
        setupRecentOrdersRecyclerView();
        startRealtimeListeners();
    }

    private void setupOrderSummaryCardClicks() {
        View.OnClickListener openAllOrders = v -> {
            Intent intent = new Intent(this, AdminOrderManagementActivity.class);
            intent.putExtra("shopId", shopId);
            intent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
            startActivity(intent);
        };
        binding.cardTotal.setOnClickListener(openAllOrders);
        binding.cardPending.setOnClickListener(v -> openOrdersWithFilter("PENDING"));
        binding.cardAccepted.setOnClickListener(v -> openOrdersWithFilter("ACCEPTED"));
        binding.cardPrinting.setOnClickListener(v -> openOrdersWithFilter("PRINTING"));
        binding.cardReady.setOnClickListener(v -> openOrdersWithFilter("READY_FOR_PICKUP"));
        binding.cardCompleted.setOnClickListener(v -> openOrdersWithFilter("COMPLETED"));
    }

    private void openOrdersWithFilter(String filter) {
        Intent intent = new Intent(this, AdminOrderManagementActivity.class);
        intent.putExtra("shopId", shopId);
        intent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
        intent.putExtra("initial_filter", filter);
        startActivity(intent);
    }

    private void setupShopStatusCard() {
        updateShopStatusUI();
    }

    private void updateShopStatusUI() {
        binding.switchShopStatus.setOnCheckedChangeListener(null);
        binding.switchShopStatus.setChecked(isShopOpen);
        if (isShopOpen) {
            binding.textStatusBadge.setText("OPEN");
            binding.textStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
            binding.textShopStatusSubtext.setText("Accepting print files from customers");
            binding.imgShopStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_ready));
            binding.cardShopStatus.setStrokeColor(Color.parseColor("#C8E6C9"));
        } else {
            binding.textStatusBadge.setText("CLOSED");
            binding.textStatusBadge.setBackgroundResource(R.drawable.bg_badge_red);
            binding.textShopStatusSubtext.setText("Closed — Customers cannot send files");
            binding.imgShopStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_rejected));
            binding.cardShopStatus.setStrokeColor(Color.parseColor("#FFCDD2"));
        }

        binding.switchShopStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == isShopOpen) return;
            isShopOpen = isChecked;
            updateShopStatusUI();

            ShopRepository.getInstance().updateShopOpenStatus(shopId, isChecked, task -> {
                if (task != null && task.isSuccessful()) {
                    String msg = isChecked
                            ? "Shop is now OPEN. Customers can send files."
                            : "Shop is now CLOSED. Customers cannot send files.";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, isChecked ? "Shop set to OPEN" : "Shop set to CLOSED", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupLocationCard() {
        updateLocationCardText();
        View.OnClickListener openPicker = v -> {
            Intent mapIntent = new Intent(this, ShopMapActivity.class);
            mapIntent.putExtra(ShopMapActivity.EXTRA_MODE, ShopMapActivity.MODE_PICKER);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_ID, shopId);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_NAME, shopName);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_ADDRESS, shopAddress);
            mapIntent.putExtra(ShopMapActivity.EXTRA_LATITUDE, shopLat);
            mapIntent.putExtra(ShopMapActivity.EXTRA_LONGITUDE, shopLng);
            locationPickerLauncher.launch(mapIntent);
        };
        binding.cardShopLocation.setOnClickListener(openPicker);
        binding.btnUpdateLocation.setOnClickListener(openPicker);

        View.OnClickListener openPricing = v -> {
            Intent intent = new Intent(this, PricingManagementActivity.class);
            intent.putExtra("shopId", shopId);
            intent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
            startActivity(intent);
        };
        binding.cardPricingAction.setOnClickListener(openPricing);
        binding.btnManagePricing.setOnClickListener(openPricing);

        View.OnClickListener openScanner = v -> {
            Intent intent = new Intent(this, PickupVerificationActivity.class);
            intent.putExtra("extra_auto_scan", true);
            startActivity(intent);
        };
        binding.cardScanQrAction.setOnClickListener(openScanner);
        binding.btnScanCustomerQr.setOnClickListener(openScanner);
    }

    private void updateLocationCardText() {
        if (shopLat != 0.0 && shopLng != 0.0) {
            binding.textShopLocationStatus.setText(
                    String.format(Locale.getDefault(), "Pinned: %.4f, %.4f", shopLat, shopLng));
            binding.btnUpdateLocation.setText("Update Pin");
        } else {
            binding.textShopLocationStatus.setText("Tap to pin or update your shop's exact location");
            binding.btnUpdateLocation.setText("Set Pin");
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                // already here
                return true;
            } else if (id == R.id.nav_orders) {
                Intent orderIntent = new Intent(this, AdminOrderManagementActivity.class);
                orderIntent.putExtra("shopId", shopId);
                orderIntent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
                startActivity(orderIntent);
                return true;
            } else if (id == R.id.nav_printers) {
                startActivity(new Intent(this, PrinterManagementActivity.class)
                        .putExtra("shopId", shopId));
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, ReportsActivity.class)
                        .putExtra("shopId", shopId));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class)
                        .putExtra("shopId", shopId));
                return true;
            }
            return false;
        });
        binding.bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
    }

    private void setupRecentOrdersRecyclerView() {
        recentOrderAdapter = new AdminOrderAdapter(recentOrders, order -> {
            Intent intent = new Intent(this, AdminOrderDetailsActivity.class);
            intent.putExtra("orderId", order.getId() != null ? order.getId() : order.getOrderId());
            intent.putExtra(Constants.EXTRA_ORDER_ID, order.getId() != null ? order.getId() : order.getOrderId());
            intent.putExtra("shopId", shopId);
            startActivity(intent);
        });
        binding.recyclerRecentOrders.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        binding.recyclerRecentOrders.setAdapter(recentOrderAdapter);
        binding.recyclerRecentOrders.setNestedScrollingEnabled(false);
    }

    // ─── Realtime Firestore Listeners ─────────────────────────────────────────

    private void startRealtimeListeners() {
        Log.d(TAG, "Starting realtime listeners for shopId=" + shopId);
        attachShopStatusListener();
        attachOrdersListener();
        attachPrintersListener();
        attachInventoryListener();
    }

    private void attachShopStatusListener() {
        if (shopId == null || shopId.isEmpty()) return;
        shopStatusListener = ShopRepository.getInstance().addShopListener(shopId, (doc, e) -> {
            if (e != null) {
                Log.e(TAG, "Shop status listener error", e);
                return;
            }
            if (doc != null && doc.exists()) {
                Boolean open = doc.getBoolean("isOpen");
                if (open == null) open = doc.getBoolean("open");
                if (open != null && open != isShopOpen) {
                    isShopOpen = open;
                    updateShopStatusUI();
                }
            }
        });
    }

    private void attachOrdersListener() {
        if (shopId == null || shopId.isEmpty()) {
            shopId = sessionManager.getShopId();
        }
        if (shopId == null || shopId.isEmpty()) return;

        // Query by shopId WITHOUT orderBy or date filter in Firestore query!
        // This eliminates the composite index requirement that caused queries to fail with FAILED_PRECONDITION!
        ordersListener = db.collection(Constants.COLLECTION_ORDERS)
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Orders listener error: " + error.getMessage(), error);
                        return;
                    }
                    if (snapshots == null) return;

                    List<PrintOrder> allShopOrders = new ArrayList<>();
                    int pending = 0, accepted = 0, printing = 0, ready = 0, completed = 0;
                    double revenue = 0.0;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        PrintOrder o = doc.toObject(PrintOrder.class);
                        if (o == null) continue;
                        o.setId(doc.getId());
                        if (o.getOrderId() == null || o.getOrderId().isEmpty()) {
                            o.setOrderId(doc.getId());
                        }
                        allShopOrders.add(o);

                        String status = o.getOrderStatus();
                        if (status == null || status.trim().isEmpty()) {
                            status = o.getStatus();
                        }
                        if (status == null) status = "";
                        status = status.trim().toUpperCase(Locale.ROOT);
                        switch (status) {
                            case "PENDING":   pending++;   break;
                            case "ACCEPTED":  accepted++;  break;
                            case "PRINTING":  printing++;  break;
                            case "READY_FOR_PICKUP": ready++; break;
                            case "COMPLETED": completed++; break;
                        }
                        if ("PAID".equalsIgnoreCase(o.getPaymentStatus()) || "COMPLETED".equalsIgnoreCase(status)) {
                            revenue += o.getTotalAmount();
                        }
                    }

                    // Sort descending in memory (latest orders first)
                    Collections.sort(allShopOrders, (o1, o2) -> {
                        Timestamp t1 = o1.getSubmittedAt() != null ? o1.getSubmittedAt() : o1.getCreatedAt();
                        Timestamp t2 = o2.getSubmittedAt() != null ? o2.getSubmittedAt() : o2.getCreatedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });

                    // Check for newly placed incoming orders and trigger notification for shopkeeper
                    if (!isFirstOrdersSnapshot) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            if (!knownOrderIds.contains(doc.getId())) {
                                knownOrderIds.add(doc.getId());
                                PrintOrder newOrd = doc.toObject(PrintOrder.class);
                                String oId = (newOrd != null && newOrd.getId() != null) ? newOrd.getId() : doc.getId();
                                NotificationUtils.createAdminAlert(
                                        AdminDashboardActivity.this,
                                        "New Order Received! 🔔",
                                        "Order #" + oId + " was placed by a customer."
                                );
                                Toast.makeText(AdminDashboardActivity.this, "🔔 New Order #" + oId + " received!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            knownOrderIds.add(doc.getId());
                        }
                        isFirstOrdersSnapshot = false;
                    }

                    updateSummaryCards(allShopOrders.size(), pending, accepted,
                            printing, ready, completed, revenue);
                    updateRecentOrders(allShopOrders);
                });
    }

    private void attachPrintersListener() {
        printersListener = db.collection("printers")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Printers listener error", error);
                        return;
                    }
                    if (snapshots == null) return;

                    int available = 0, busy = 0, outOfService = 0;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String status = doc.getString("status");
                        if ("AVAILABLE".equals(status)) available++;
                        else if ("BUSY".equals(status)) busy++;
                        else if ("OUT_OF_SERVICE".equals(status)) outOfService++;
                    }
                    updatePrinterStatus(available, busy, outOfService);
                });
    }

    private void attachInventoryListener() {
        inventoryListener = db.collection("inventory")
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Inventory listener error", error);
                        return;
                    }
                    if (snapshots == null) return;

                    List<String> lowStockItems = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        InventoryItem item = doc.toObject(InventoryItem.class);
                        if (item == null) continue;
                        if (item.getQuantity() <= item.getThreshold()) {
                            lowStockItems.add(item.getName() + " (" + item.getQuantity()
                                    + " " + item.getUnit() + " left)");
                        }
                    }
                    updateLowStockAlerts(lowStockItems);
                });
    }

    // ─── UI Update Methods ────────────────────────────────────────────────────

    private void updateSummaryCards(int total, int pending, int accepted,
                                    int printing, int ready, int completed, double revenue) {
        runOnUiThread(() -> {
            binding.textTotalOrders.setText(String.valueOf(total));
            binding.textPendingOrders.setText(String.valueOf(pending));
            binding.textAcceptedOrders.setText(String.valueOf(accepted));
            binding.textPrintingOrders.setText(String.valueOf(printing));
            binding.textReadyOrders.setText(String.valueOf(ready));
            binding.textCompletedOrders.setText(String.valueOf(completed));
            binding.textRevenue.setText(String.format(Locale.getDefault(), "BDT %.2f", revenue));

            // Highlight pending if non-zero
            if (pending > 0) {
                binding.cardPending.setStrokeColor(getColor(R.color.status_pending));
                binding.cardPending.setStrokeWidth(4);
            } else {
                binding.cardPending.setStrokeWidth(0);
            }
        });
    }

    private void updateRecentOrders(List<PrintOrder> orders) {
        runOnUiThread(() -> {
            recentOrders.clear();
            int limit = Math.min(orders.size(), 5);
            recentOrders.addAll(orders.subList(0, limit));
            recentOrderAdapter.updateList(recentOrders);
            binding.textNoRecentOrders.setVisibility(
                    recentOrders.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void updatePrinterStatus(int available, int busy, int outOfService) {
        runOnUiThread(() -> {
            binding.textPrintersAvailable.setText(available + " Available");
            binding.textPrintersBusy.setText(busy + " Busy");
            binding.textPrintersOutOfService.setText(outOfService + " Out of Service");
        });
    }

    private void updateLowStockAlerts(List<String> lowStockItems) {
        runOnUiThread(() -> {
            if (lowStockItems.isEmpty()) {
                binding.cardLowStock.setVisibility(View.GONE);
            } else {
                binding.cardLowStock.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (String item : lowStockItems) {
                    sb.append("• ").append(item).append("\n");
                }
                binding.textLowStockItems.setText(sb.toString().trim());
            }
        });
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            logout();
            return true;
        } else if (id == R.id.action_inventory) {
            startActivity(new Intent(this, InventoryManagementActivity.class)
                    .putExtra("shopId", shopId));
            return true;
        } else if (id == R.id.action_pricing) {
            startActivity(new Intent(this, PricingManagementActivity.class)
                    .putExtra("shopId", shopId));
            return true;
        } else if (id == R.id.action_shop_location) {
            Intent mapIntent = new Intent(this, ShopMapActivity.class);
            mapIntent.putExtra(ShopMapActivity.EXTRA_MODE, ShopMapActivity.MODE_PICKER);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_ID, shopId);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_NAME, shopName);
            mapIntent.putExtra(ShopMapActivity.EXTRA_SHOP_ADDRESS, shopAddress);
            mapIntent.putExtra(ShopMapActivity.EXTRA_LATITUDE, shopLat);
            mapIntent.putExtra(ShopMapActivity.EXTRA_LONGITUDE, shopLng);
            locationPickerLauncher.launch(mapIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        Log.d(TAG, "Admin logging out");
        auth.signOut();
        Intent intent = new Intent(this, AdminLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void redirectToLogin() {
        Intent intent = new Intent(this, AdminLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy — detaching listeners");
        if (ordersListener != null) ordersListener.remove();
        if (printersListener != null) printersListener.remove();
        if (inventoryListener != null) inventoryListener.remove();
        if (shopStatusListener != null) shopStatusListener.remove();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }
}
