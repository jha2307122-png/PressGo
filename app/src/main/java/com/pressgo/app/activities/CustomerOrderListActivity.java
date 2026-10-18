package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.ListenerRegistration;
import com.pressgo.app.adapters.OrderAdapter;
import com.pressgo.app.databinding.ActivityCustomerOrderListBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.repositories.UserRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class CustomerOrderListActivity extends AppCompatActivity {
    private static final String TAG = "CustomerOrderList";

    private ActivityCustomerOrderListBinding binding;
    private final List<PrintOrder> allOrders = new ArrayList<>();
    private final List<PrintOrder> displayedOrders = new ArrayList<>();
    private OrderAdapter adapter;
    private ListenerRegistration ordersListener;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityCustomerOrderListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Print Orders");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupFilters();
        loadOrders();
        attachOrderListener();
    }

    private String resolveCurrentCustomerId() {
        if (UserRepository.getInstance().getCurrentUser() != null) {
            String uid = UserRepository.getInstance().getCurrentUser().getUid();
            SessionManager sm = new SessionManager(this);
            if (sm.getUserId() == null || !uid.equals(sm.getUserId())) {
                sm.saveUserSession(uid, Constants.ROLE_CUSTOMER, sm.getShopId());
            }
            return uid;
        }
        SessionManager sm = new SessionManager(this);
        String uid = sm.getUserId();
        if (uid != null && !uid.trim().isEmpty()) {
            return uid;
        }
        return "demo_cust_uid";
    }

    private void setupRecyclerView() {
        adapter = new OrderAdapter(this, displayedOrders, order -> {
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            String orderId = order.getId() != null && !order.getId().isEmpty() ? order.getId() : order.getOrderId();
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });
        binding.rvOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvOrders.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(this::loadOrders);
    }

    private void setupFilters() {
        binding.chipAll.setOnClickListener(v -> filterOrders("ALL"));
        binding.chipActive.setOnClickListener(v -> filterOrders("ACTIVE"));
        binding.chipCompleted.setOnClickListener(v -> filterOrders("COMPLETED"));
    }

    private void attachOrderListener() {
        String uid = resolveCurrentCustomerId();
        if (ordersListener != null) {
            ordersListener.remove();
        }
        ordersListener = OrderRepository.getInstance().addCustomerOrdersListener(uid, (snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Orders listener error", e);
                return;
            }
            loadOrders();
        });
    }

    private void loadOrders() {
        String uid = resolveCurrentCustomerId();
        String email = UserRepository.getInstance().getCurrentUser() != null
                ? UserRepository.getInstance().getCurrentUser().getEmail()
                : null;

        binding.swipeRefreshLayout.setRefreshing(true);
        OrderRepository.getInstance().getCustomerOrders(uid, email, orders -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            allOrders.clear();
            if (orders != null) {
                allOrders.addAll(orders);
            }
            filterOrders(currentFilter);
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Failed to load orders", e);
            updateEmptyState();
        });
    }

    private void filterOrders(String filter) {
        this.currentFilter = filter;
        displayedOrders.clear();
        for (PrintOrder order : allOrders) {
            String status = order.getOrderStatus();
            if (status == null || status.trim().isEmpty()) {
                status = order.getStatus();
            }
            if (status == null) status = "";

            if ("ALL".equals(filter)) {
                displayedOrders.add(order);
            } else if ("ACTIVE".equals(filter)) {
                if (PrintOrder.STATUS_PENDING.equalsIgnoreCase(status) ||
                        PrintOrder.STATUS_ACCEPTED.equalsIgnoreCase(status) ||
                        PrintOrder.STATUS_PRINTING.equalsIgnoreCase(status) ||
                        PrintOrder.STATUS_READY_FOR_PICKUP.equalsIgnoreCase(status)) {
                    displayedOrders.add(order);
                }
            } else if ("COMPLETED".equals(filter)) {
                if (PrintOrder.STATUS_COMPLETED.equalsIgnoreCase(status)) {
                    displayedOrders.add(order);
                }
            }
        }
        adapter.updateList(displayedOrders);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (displayedOrders.isEmpty()) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.rvOrders.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.rvOrders.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
        if (ordersListener == null) {
            attachOrderListener();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (ordersListener != null) {
            ordersListener.remove();
        }
    }
}
