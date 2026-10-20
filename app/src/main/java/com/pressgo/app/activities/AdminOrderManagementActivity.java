package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.pressgo.app.R;
import com.pressgo.app.adapters.AdminOrderAdapter;
import com.pressgo.app.databinding.ActivityAdminOrderManagementBinding;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.repositories.OrderRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AdminOrderManagementActivity extends AppCompatActivity
        implements AdminOrderAdapter.OnOrderClickListener, AdminOrderAdapter.OnOrderLongClickListener {

    private static final String TAG = "AdminOrderMgmtActivity";

    private ActivityAdminOrderManagementBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    private String shopId;

    private AdminOrderAdapter adapter;
    private final List<PrintOrder> allOrders = new ArrayList<>();
    private final List<PrintOrder> filteredOrders = new ArrayList<>();
    private final List<String> selectedOrderIds = new ArrayList<>();

    private String currentFilter = "ALL";
    private String searchQuery = "";
    private boolean sortByPriority = false;

    private ListenerRegistration ordersListener;
    private OrderRepository orderRepository;

    // Status counts
    private int cntAll, cntPending, cntAccepted, cntPrinting, cntReady, cntCompleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityAdminOrderManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Order Management");
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        orderRepository = OrderRepository.getInstance();

        shopId = getIntent().getStringExtra("shopId");
        if (shopId == null || shopId.isEmpty()) {
            shopId = getIntent().getStringExtra(Constants.EXTRA_SHOP_ID);
        }
        if (shopId == null || shopId.isEmpty()) {
            shopId = sessionManager.getShopId();
        }

        String initialFilter = getIntent().getStringExtra("initial_filter");
        if (initialFilter != null && !initialFilter.trim().isEmpty()) {
            currentFilter = initialFilter.trim().toUpperCase(Locale.ROOT);
        }

        setupRecyclerView();
        setupSearchView();
        setupFilterChips();
        setupSortToggle();
        setupSwipeRefresh();
        setupBatchActionBar();

        if (shopId != null && !shopId.isEmpty()) {
            attachOrdersListener();
        } else {
            resolveShopIdAndAttach();
        }
    }

    private void resolveShopIdAndAttach() {
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            db.collection("shops").whereEqualTo("ownerId", uid).limit(1).get()
                    .addOnSuccessListener(querySnapshots -> {
                        if (!querySnapshots.isEmpty()) {
                            shopId = querySnapshots.getDocuments().get(0).getId();
                            sessionManager.setShopId(shopId);
                            attachOrdersListener();
                        } else {
                            Toast.makeText(this, "Shop not found for this account", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to locate shop: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
        } else {
            Toast.makeText(this, "Missing shopId", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new AdminOrderAdapter(filteredOrders, this, this);
        adapter.setQuickActionListener((order, action) -> {
            String oid = order.getId() != null ? order.getId() : order.getOrderId();
            if (AdminOrderAdapter.ACTION_ACCEPT.equals(action)) {
                orderRepository.updateOrderStatus(oid, "ACCEPTED", null,
                        unused -> Toast.makeText(this, "Order accepted", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed to accept: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else if (AdminOrderAdapter.ACTION_START_PRINT.equals(action)) {
                orderRepository.updateOrderStatus(oid, "PRINTING", null,
                        unused -> Toast.makeText(this, "Printing started", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else if (AdminOrderAdapter.ACTION_MARK_READY.equals(action)) {
                orderRepository.updateOrderStatus(oid, "READY_FOR_PICKUP", null,
                        unused -> Toast.makeText(this, "Marked ready for pickup", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        binding.recyclerOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerOrders.setAdapter(adapter);
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private void setupSearchView() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // ─── Filter Chips ─────────────────────────────────────────────────────────

    private void setupFilterChips() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipAll) setFilter("ALL");
            else if (id == R.id.chipPending) setFilter("PENDING");
            else if (id == R.id.chipAccepted) setFilter("ACCEPTED");
            else if (id == R.id.chipPrinting) setFilter("PRINTING");
            else if (id == R.id.chipReady) setFilter("READY_FOR_PICKUP");
            else if (id == R.id.chipCompleted) setFilter("COMPLETED");
        });
        updateChipCheckedState();
    }

    private void setFilter(String filter) {
        if (filter == null) filter = "ALL";
        currentFilter = filter;
        updateChipCheckedState();
        applyFilters();
    }

    private void updateChipCheckedState() {
        int targetId = R.id.chipAll;
        if ("PENDING".equalsIgnoreCase(currentFilter)) targetId = R.id.chipPending;
        else if ("ACCEPTED".equalsIgnoreCase(currentFilter)) targetId = R.id.chipAccepted;
        else if ("PRINTING".equalsIgnoreCase(currentFilter)) targetId = R.id.chipPrinting;
        else if ("READY_FOR_PICKUP".equalsIgnoreCase(currentFilter)) targetId = R.id.chipReady;
        else if ("COMPLETED".equalsIgnoreCase(currentFilter)) targetId = R.id.chipCompleted;

        if (binding.chipGroupStatus.getCheckedChipId() != targetId) {
            binding.chipGroupStatus.check(targetId);
        }
    }

    private void updateChipCounts() {
        binding.chipAll.setText("All (" + cntAll + ")");
        binding.chipPending.setText("Pending (" + cntPending + ")");
        binding.chipAccepted.setText("Accepted (" + cntAccepted + ")");
        binding.chipPrinting.setText("Printing (" + cntPrinting + ")");
        binding.chipReady.setText("Ready (" + cntReady + ")");
        binding.chipCompleted.setText("Completed (" + cntCompleted + ")");
    }

    // ─── Sort Toggle ──────────────────────────────────────────────────────────

    private void setupSortToggle() {
        binding.btnSort.setOnClickListener(v -> {
            sortByPriority = !sortByPriority;
            binding.btnSort.setText(sortByPriority ? "Sort: Priority" : "Sort: Newest");
            applyFilters();
        });
    }

    // ─── Swipe Refresh ────────────────────────────────────────────────────────

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "Pull-to-refresh triggered");
            if (ordersListener != null) ordersListener.remove();
            attachOrdersListener();
        });
    }

    // ─── Batch Action Bar ─────────────────────────────────────────────────────

    private void setupBatchActionBar() {
        binding.batchActionBar.setVisibility(View.GONE);
        binding.btnAcceptSelected.setOnClickListener(v -> acceptSelectedOrders());
        binding.btnClearSelection.setOnClickListener(v -> clearSelection());
    }

    private void showBatchBar() {
        binding.batchActionBar.setVisibility(View.VISIBLE);
        binding.textSelectedCount.setText(selectedOrderIds.size() + " selected");
    }

    private void hideBatchBar() {
        binding.batchActionBar.setVisibility(View.GONE);
    }

    private void clearSelection() {
        selectedOrderIds.clear();
        adapter.clearSelection();
        hideBatchBar();
    }

    private void acceptSelectedOrders() {
        if (selectedOrderIds.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Accept Orders")
                .setMessage("Accept " + selectedOrderIds.size() + " selected orders?")
                .setPositiveButton("Accept All", (d, w) -> {
                    for (String orderId : selectedOrderIds) {
                        orderRepository.updateOrderStatus(orderId, "ACCEPTED", null,
                                unused -> Log.d(TAG, "Accepted " + orderId),
                                e -> Log.e(TAG, "Failed to accept " + orderId, e));
                    }
                    clearSelection();
                    Toast.makeText(this, "Orders accepted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Firestore Listener ───────────────────────────────────────────────────

    private void attachOrdersListener() {
        if (shopId == null || shopId.isEmpty()) return;
        Log.d(TAG, "Attaching orders listener for shopId=" + shopId);
        if (ordersListener != null) {
            ordersListener.remove();
        }
        // Do NOT use orderBy("createdAt") here to avoid requiring a composite index in Firestore!
        ordersListener = db.collection(Constants.COLLECTION_ORDERS)
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener((snapshots, error) -> {
                    binding.swipeRefresh.setRefreshing(false);
                    if (error != null) {
                        Log.e(TAG, "Orders listener error", error);
                        Toast.makeText(this, "Failed to load orders: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots == null) return;

                    allOrders.clear();
                    cntAll = cntPending = cntAccepted = cntPrinting = cntReady = cntCompleted = 0;

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        PrintOrder o = doc.toObject(PrintOrder.class);
                        if (o == null) continue;
                        o.setId(doc.getId());
                        if (o.getOrderId() == null || o.getOrderId().isEmpty()) {
                            o.setOrderId(doc.getId());
                        }
                        allOrders.add(o);
                        cntAll++;
                        String st = o.getOrderStatus();
                        if (st == null || st.trim().isEmpty()) {
                            st = o.getStatus();
                        }
                        if (st == null) st = "";
                        st = st.trim().toUpperCase(Locale.ROOT);
                        switch (st) {
                            case "PENDING":          cntPending++;   break;
                            case "ACCEPTED":         cntAccepted++;  break;
                            case "PRINTING":         cntPrinting++;  break;
                            case "READY_FOR_PICKUP": cntReady++;     break;
                            case "COMPLETED":        cntCompleted++; break;
                        }
                    }

                    // In-memory sort descending by date (latest orders first)
                    Collections.sort(allOrders, (a, b) -> {
                        Timestamp t1 = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
                        Timestamp t2 = b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });

                    runOnUiThread(() -> {
                        updateChipCounts();
                        applyFilters();
                    });
                });
    }

    // ─── Filter + Sort Logic ──────────────────────────────────────────────────

    private void applyFilters() {
        filteredOrders.clear();

        for (PrintOrder o : allOrders) {
            // Status filter
            String st = o.getOrderStatus();
            if (st == null || st.trim().isEmpty()) {
                st = o.getStatus();
            }
            if (st == null) st = "";
            st = st.trim();
            if (!"ALL".equalsIgnoreCase(currentFilter) && !currentFilter.equalsIgnoreCase(st)) continue;

            // Search filter
            if (!searchQuery.isEmpty()) {
                String id = o.getOrderId() != null ? o.getOrderId().toLowerCase(Locale.ROOT) : "";
                String name = o.getCustomerName() != null ? o.getCustomerName().toLowerCase(Locale.ROOT) : "";
                if (!id.contains(searchQuery) && !name.contains(searchQuery)) continue;
            }

            filteredOrders.add(o);
        }

        // Sort
        Collections.sort(filteredOrders, (a, b) -> {
            if (sortByPriority) {
                boolean ap = Boolean.TRUE.equals(a.isPriority());
                boolean bp = Boolean.TRUE.equals(b.isPriority());
                int pComp = Boolean.compare(bp, ap); // priority first
                if (pComp != 0) return pComp;
            }
            Timestamp t1 = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
            Timestamp t2 = b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        adapter.updateList(filteredOrders);

        // Empty state
        binding.textEmptyOrders.setVisibility(filteredOrders.isEmpty() ? View.VISIBLE : View.GONE);
        binding.textEmptyOrders.setText("No orders" +
                ("ALL".equalsIgnoreCase(currentFilter) ? "" : " with status " + currentFilter));
    }

    // ─── Adapter Callbacks ────────────────────────────────────────────────────

    @Override
    public void onOrderClick(PrintOrder order) {
        if (!selectedOrderIds.isEmpty()) {
            // In selection mode: toggle selection
            toggleSelection(order.getOrderId());
            return;
        }
        Intent intent = new Intent(this, AdminOrderDetailsActivity.class);
        String oid = order.getId() != null ? order.getId() : order.getOrderId();
        intent.putExtra(Constants.EXTRA_ORDER_ID, oid);
        intent.putExtra("orderId", oid);
        intent.putExtra("shopId", shopId);
        intent.putExtra(Constants.EXTRA_SHOP_ID, shopId);
        startActivity(intent);
    }

    @Override
    public void onOrderLongClick(PrintOrder order) {
        toggleSelection(order.getOrderId());
    }

    private void toggleSelection(String orderId) {
        if (selectedOrderIds.contains(orderId)) {
            selectedOrderIds.remove(orderId);
            adapter.deselectOrder(orderId);
        } else {
            selectedOrderIds.add(orderId);
            adapter.selectOrder(orderId);
        }
        if (selectedOrderIds.isEmpty()) {
            hideBatchBar();
        } else {
            showBatchBar();
        }
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_orders, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (shopId != null && !shopId.isEmpty() && ordersListener == null) {
            attachOrdersListener();
        } else {
            applyFilters();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy — removing listener");
        if (ordersListener != null) ordersListener.remove();
    }
}
