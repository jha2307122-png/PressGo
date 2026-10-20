package com.pressgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.R;
import com.pressgo.app.adapters.InventoryAdapter;
import com.pressgo.app.databinding.ActivityInventoryManagementBinding;
import com.pressgo.app.models.InventoryItem;
import com.pressgo.app.repositories.InventoryRepository;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryManagementActivity extends AppCompatActivity {
    private static final String TAG = "InventoryManagement";

    private ActivityInventoryManagementBinding binding;
    private final List<InventoryItem> items = new ArrayList<>();
    private InventoryAdapter adapter;
    private String shopId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityInventoryManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopId = new SessionManager(this).getShopId();
        if (shopId == null || shopId.isEmpty()) {
            shopId = "shop_campus_main";
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Inventory & Supplies");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupFab();
        loadInventory();
    }

    private void setupRecyclerView() {
        adapter = new InventoryAdapter(items, new InventoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(InventoryItem item) {
                showEditQuantityDialog(item);
            }

            @Override
            public void onItemLongClick(InventoryItem item) {
                confirmDeleteItem(item);
            }
        });
        binding.rvInventory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvInventory.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(this::loadInventory);
    }

    private void setupFab() {
        binding.fabAddItem.setOnClickListener(v -> showAddItemDialog());
    }

    private void loadInventory() {
        binding.swipeRefreshLayout.setRefreshing(true);
        InventoryRepository.getInstance().getShopInventory(shopId, list -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            items.clear();
            if (list != null && !list.isEmpty()) {
                items.addAll(list);
            } else {
                initDefaultInventory();
            }
            adapter.notifyDataSetChanged();
            checkLowStockAlerts();
            updateEmptyState();
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Error fetching inventory", e);
            initDefaultInventory();
        });
    }

    private void initDefaultInventory() {
        items.clear();

        InventoryItem i1 = new InventoryItem();
        i1.setId("inv_a4_paper");
        i1.setShopId(shopId);
        i1.setItemName("A4 80gsm Paper Reams");
        i1.setCategory("PAPER");
        i1.setQuantity(25);
        i1.setUnit("reams");
        i1.setLowStockThreshold(10);

        InventoryItem i2 = new InventoryItem();
        i2.setId("inv_black_toner");
        i2.setShopId(shopId);
        i2.setItemName("Black Toner Cartridge CF289A");
        i2.setCategory("TONER");
        i2.setQuantity(2);
        i2.setUnit("cartridges");
        i2.setLowStockThreshold(3);

        InventoryItem i3 = new InventoryItem();
        i3.setId("inv_spiral_comb");
        i3.setShopId(shopId);
        i3.setItemName("Spiral Plastic Binding Combs 12mm");
        i3.setCategory("BINDING_MATERIAL");
        i3.setQuantity(80);
        i3.setUnit("pieces");
        i3.setLowStockThreshold(20);

        items.add(i1);
        items.add(i2);
        items.add(i3);

        InventoryRepository.getInstance().addInventoryItem(i1, t -> {});
        InventoryRepository.getInstance().addInventoryItem(i2, t -> {});
        InventoryRepository.getInstance().addInventoryItem(i3, t -> {});

        adapter.notifyDataSetChanged();
        checkLowStockAlerts();
        updateEmptyState();
    }

    private void checkLowStockAlerts() {
        int lowStockCount = 0;
        for (InventoryItem i : items) {
            if (i.getQuantity() <= i.getLowStockThreshold()) {
                lowStockCount++;
            }
        }

        if (lowStockCount > 0) {
            binding.cardLowStockAlert.setVisibility(View.VISIBLE);
            binding.tvLowStockAlertMessage.setText(lowStockCount + " item(s) are running low! Reorder supplies soon.");
        } else {
            binding.cardLowStockAlert.setVisibility(View.GONE);
        }
    }

    private void showEditQuantityDialog(InventoryItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Quantity: " + item.getItemName());
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(item.getQuantity()));
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            try {
                int qty = Integer.parseInt(input.getText().toString());
                item.setQuantity(qty);
                InventoryRepository.getInstance().updateInventoryItem(item, task -> {
                    adapter.notifyDataSetChanged();
                    checkLowStockAlerts();
                    Toast.makeText(this, "Quantity updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception ignored) {}
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void confirmDeleteItem(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Inventory Item")
                .setMessage("Are you sure you want to delete '" + item.getItemName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    InventoryRepository.getInstance().deleteInventoryItem(item.getId(), task -> {
                        items.remove(item);
                        adapter.notifyDataSetChanged();
                        checkLowStockAlerts();
                        updateEmptyState();
                        Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddItemDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_inventory, null);
        EditText etName = view.findViewById(R.id.etItemName);
        Spinner spCategory = view.findViewById(R.id.spinnerCategory);
        EditText etQty = view.findViewById(R.id.etItemQuantity);
        EditText etUnit = view.findViewById(R.id.etItemUnit);
        EditText etThreshold = view.findViewById(R.id.etItemThreshold);

        String[] categories = {"PAPER", "INK", "TONER", "BINDING_MATERIAL", "OTHER"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        spCategory.setAdapter(catAdapter);

        new AlertDialog.Builder(this)
                .setTitle("Add Inventory Item")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        InventoryItem item = new InventoryItem();
                        item.setShopId(shopId);
                        item.setItemName(name);
                        item.setCategory((String) spCategory.getSelectedItem());
                        try {
                            item.setQuantity(Integer.parseInt(etQty.getText().toString().trim()));
                        } catch (Exception e) {
                            item.setQuantity(0);
                        }
                        item.setUnit(etUnit.getText().toString().trim());
                        try {
                            item.setLowStockThreshold(Integer.parseInt(etThreshold.getText().toString().trim()));
                        } catch (Exception e) {
                            item.setLowStockThreshold(5);
                        }

                        InventoryRepository.getInstance().addInventoryItem(item, task -> loadInventory());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateEmptyState() {
        if (items.isEmpty()) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.rvInventory.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.rvInventory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
