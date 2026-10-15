package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.pressgo.app.R;
import com.pressgo.app.adapters.ShopAdapter;
import com.pressgo.app.databinding.ActivityShopListBinding;
import com.pressgo.app.models.Shop;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class ShopListActivity extends AppCompatActivity {

    private static final String TAG = "ShopListActivity";

    private ActivityShopListBinding binding;
    private ShopRepository shopRepository;
    private ShopAdapter shopAdapter;

    /** Full list fetched from Firestore */
    private List<Shop> allShops = new ArrayList<>();
    /** Currently displayed list (after filter) */
    private List<Shop> displayedShops = new ArrayList<>();

    private boolean showOnlyOpen = false;
    private String currentQuery = "";

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityShopListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopRepository = ShopRepository.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Select a Shop");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String incomingPath = getIntent().getStringExtra("extra_file_path");
        Uri incomingUri = getIntent().getParcelableExtra("extra_file_uri");
        if (incomingPath != null || incomingUri != null) {
            showOnlyOpen = true;
        }

        setupRecyclerView();
        setupSearch();
        setupFilter();
        setupSwipeRefresh();

        loadShops();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        shopAdapter = new ShopAdapter(displayedShops, shop -> {
            String targetShopId = shop.getId() != null ? shop.getId() : shop.getShopId();
            String incomingPath = getIntent().getStringExtra("extra_file_path");
            Uri incomingUri = getIntent().getParcelableExtra("extra_file_uri");

            if (incomingPath != null || incomingUri != null) {
                if (!shop.isOpen()) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Shop is Closed")
                            .setMessage("\"" + shop.getName() + "\" is currently closed and not accepting print orders.\n\nPlease choose an open shop or wait for the shopkeeper to open.")
                            .setPositiveButton("Choose Another Shop", null)
                            .show();
                    return;
                }
                Intent intent = new Intent(this, DocumentUploadActivity.class);
                intent.putExtra(Constants.EXTRA_SHOP_ID, targetShopId);
                intent.putExtra("extra_file_path", incomingPath);
                intent.putExtra("extra_file_uri", incomingUri);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, ShopDetailsActivity.class);
                intent.putExtra(ShopDetailsActivity.EXTRA_SHOP_ID, targetShopId);
                intent.putExtra(Constants.EXTRA_SHOP_ID, targetShopId);
                startActivity(intent);
            }
        });

        binding.rvShops.setLayoutManager(new LinearLayoutManager(this));
        binding.rvShops.setAdapter(shopAdapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilter() {
        // Default: show only open shops
        binding.switchOpenOnly.setChecked(showOnlyOpen);
        binding.switchOpenOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showOnlyOpen = isChecked;
            applyFilters();
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener(this::loadShops);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadShops() {
        showLoading(true);

        shopRepository.getAllActiveShops(shops -> {
            showLoading(false);
            binding.swipeRefresh.setRefreshing(false);

            allShops.clear();
            if (shops != null) {
                allShops.addAll(shops);
            }
            applyFilters();
        }, e -> {
            Log.e(TAG, "Failed to load shops", e);
            showLoading(false);
            binding.swipeRefresh.setRefreshing(false);
            showEmptyState("Failed to load shops. Pull to refresh.");
            Toast.makeText(this, "Error loading shops: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filtering
    // ─────────────────────────────────────────────────────────────────────────

    private void applyFilters() {
        displayedShops.clear();

        for (Shop shop : allShops) {
            // Open-only filter
            if (showOnlyOpen && !shop.isOpen()) {
                continue;
            }
            // Search query filter
            if (!currentQuery.isEmpty()) {
                String name    = shop.getName() != null    ? shop.getName().toLowerCase()    : "";
                String address = shop.getAddress() != null ? shop.getAddress().toLowerCase() : "";
                String q       = currentQuery.toLowerCase();
                if (!name.contains(q) && !address.contains(q)) {
                    continue;
                }
            }
            displayedShops.add(shop);
        }

        shopAdapter.updateList(displayedShops);

        if (displayedShops.isEmpty()) {
            String msg = allShops.isEmpty()
                    ? "No shops available."
                    : showOnlyOpen ? "No open shops match your search." : "No shops match your search.";
            showEmptyState(msg);
        } else {
            hideEmptyState();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI state helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.rvShops.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading) {
            binding.tvEmptyState.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(String message) {
        binding.tvEmptyState.setText(message);
        binding.tvEmptyState.setVisibility(View.VISIBLE);
        binding.rvShops.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        binding.tvEmptyState.setVisibility(View.GONE);
        binding.rvShops.setVisibility(View.VISIBLE);
    }
}
