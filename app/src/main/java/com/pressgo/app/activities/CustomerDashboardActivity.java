package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.pressgo.app.R;
import com.pressgo.app.adapters.RecentOrderAdapter;
import com.pressgo.app.databinding.ActivityCustomerDashboardBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.NotificationRepository;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class CustomerDashboardActivity extends AppCompatActivity {
    private static final String TAG = "CustomerDashboard";

    private ActivityCustomerDashboardBinding binding;
    private SessionManager sessionManager;
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private RecentOrderAdapter recentOrderAdapter;
    private final List<PrintOrder> recentOrders = new ArrayList<>();
    private ListenerRegistration notificationListener;
    private ListenerRegistration ordersListener;
    private final java.util.Set<String> notifiedIds = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityCustomerDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager  = new SessionManager(this);
        userRepository  = UserRepository.getInstance();
        orderRepository = OrderRepository.getInstance();

        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        // Initialize notification channels & request permission on Android 13+
        com.pressgo.app.utils.NotificationUtils.createNotificationChannels(this);
        com.pressgo.app.utils.NotificationUtils.requestNotificationPermission(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("PressGo Dashboard");
        }

        setupGreeting();
        setupDashboardCards();
        setupBottomNavigation();
        setupRecentOrders();
        attachNotificationListener();
    }

    private void setupGreeting() {
        userRepository.getCurrentUserProfile(user -> {
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                binding.tvGreeting.setText("Hello, " + user.getDisplayName() + "!");
            } else {
                binding.tvGreeting.setText("Welcome back!");
            }
        }, e -> binding.tvGreeting.setText("Welcome back!"));
    }

    private void setupDashboardCards() {
        binding.btnFindShops.setOnClickListener(v ->
                startActivity(new Intent(this, ShopListActivity.class)));

        binding.cardNewOrder.setOnClickListener(v ->
                startActivity(new Intent(this, ShopListActivity.class)));

        binding.cardMyOrders.setOnClickListener(v ->
                startActivity(new Intent(this, CustomerOrderListActivity.class)));

        binding.cardConvertDocs.setOnClickListener(v ->
                startActivity(new Intent(this, ConversionCenterActivity.class)));

        binding.cardCompressFiles.setOnClickListener(v ->
                startActivity(new Intent(this, FileCompressionActivity.class)));

        binding.cardNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        binding.cardHelpSupport.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        binding.btnViewAllOrders.setOnClickListener(v ->
                startActivity(new Intent(this, CustomerOrderListActivity.class)));
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                return true;
            } else if (id == R.id.nav_orders) {
                startActivity(new Intent(this, CustomerOrderListActivity.class));
                return true;
            } else if (id == R.id.nav_convert) {
                startActivity(new Intent(this, ConversionCenterActivity.class));
                return true;
            } else if (id == R.id.nav_compress) {
                startActivity(new Intent(this, FileCompressionActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private String resolveCurrentCustomerId() {
        if (userRepository.getCurrentUser() != null) {
            String uid = userRepository.getCurrentUser().getUid();
            if (sessionManager.getUserId() == null || !uid.equals(sessionManager.getUserId())) {
                sessionManager.saveUserSession(uid, Constants.ROLE_CUSTOMER, sessionManager.getShopId());
            }
            return uid;
        }
        String uid = sessionManager.getUserId();
        if (uid != null && !uid.trim().isEmpty()) {
            return uid;
        }
        return "demo_cust_uid";
    }

    private void setupRecentOrders() {
        recentOrderAdapter = new RecentOrderAdapter(recentOrders, order -> {
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            String orderId = order.getId() != null && !order.getId().isEmpty() ? order.getId() : order.getOrderId();
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });

        binding.rvRecentOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRecentOrders.setAdapter(recentOrderAdapter);
        binding.rvRecentOrders.setNestedScrollingEnabled(false);

        loadRecentOrders();
        attachOrderListener();
    }

    private void attachOrderListener() {
        String uid = resolveCurrentCustomerId();
        if (ordersListener != null) {
            ordersListener.remove();
        }
        ordersListener = orderRepository.addCustomerOrdersListener(uid, (snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Customer orders listener error", e);
                return;
            }
            loadRecentOrders();
        });
    }

    private void loadRecentOrders() {
        String uid = resolveCurrentCustomerId();
        String email = userRepository.getCurrentUser() != null ? userRepository.getCurrentUser().getEmail() : null;

        orderRepository.getCustomerOrders(uid, email, orders -> {
            recentOrders.clear();
            if (orders != null && !orders.isEmpty()) {
                int limit = Math.min(orders.size(), 5);
                for (int i = 0; i < limit; i++) {
                    recentOrders.add(orders.get(i));
                }
                binding.tvNoRecentOrders.setVisibility(View.GONE);
                binding.rvRecentOrders.setVisibility(View.VISIBLE);
            } else {
                binding.tvNoRecentOrders.setVisibility(View.VISIBLE);
                binding.rvRecentOrders.setVisibility(View.GONE);
            }
            recentOrderAdapter.notifyDataSetChanged();
        }, e -> {
            Log.e(TAG, "Failed to load recent orders", e);
            if (recentOrders.isEmpty()) {
                binding.tvNoRecentOrders.setVisibility(View.VISIBLE);
                binding.rvRecentOrders.setVisibility(View.GONE);
            }
        });
    }

    private void attachNotificationListener() {
        String uid = resolveCurrentCustomerId();
        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = NotificationRepository.getInstance().addNotificationsListener(uid, (snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Customer notifications listener error", e);
                return;
            }
            if (snapshot == null) return;

            for (com.google.firebase.firestore.DocumentChange dc : snapshot.getDocumentChanges()) {
                if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                    dc.getType() == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                    com.pressgo.app.models.Notification n = dc.getDocument().toObject(com.pressgo.app.models.Notification.class);
                    if (n != null && !n.isRead()) {
                        String notifId = dc.getDocument().getId();
                        if (!notifiedIds.contains(notifId)) {
                            notifiedIds.add(notifId);
                            com.pressgo.app.utils.NotificationUtils.createOrderNotification(
                                    CustomerDashboardActivity.this,
                                    n.getTitle() != null ? n.getTitle() : "PressGo Order Update",
                                    n.getMessage() != null ? n.getMessage() : "Your order status was updated.",
                                    n.getOrderId()
                            );
                        }
                    }
                }
            }

            int unread = 0;
            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                Boolean read = doc.getBoolean("read");
                if (read == null || !read) unread++;
            }
            updateNotificationBadge(unread);
        });
    }

    private void updateNotificationBadge(int count) {
        if (binding != null) {
            if (binding.tvNotificationsCardTitle != null) {
                if (count > 0) {
                    binding.tvNotificationsCardTitle.setText("Notifications (" + count + ")");
                } else {
                    binding.tvNotificationsCardTitle.setText("Notifications");
                }
            }
            if (binding.bottomNavigation != null) {
                BadgeDrawable badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_orders);
                if (count > 0) {
                    badge.setVisible(true);
                    badge.setNumber(count);
                } else {
                    badge.setVisible(false);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_customer_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
            return true;
        } else if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out of PressGo?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    userRepository.logout();
                    sessionManager.clearSession();
                    redirectToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentOrders();
        if (ordersListener == null) {
            attachOrderListener();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (notificationListener != null) {
            notificationListener.remove();
        }
        if (ordersListener != null) {
            ordersListener.remove();
        }
    }
}
