package com.pressgo.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.R;
import com.pressgo.app.adapters.PrinterAdapter;
import com.pressgo.app.databinding.ActivityShopDetailsBinding;
import com.pressgo.app.models.Shop;
import com.pressgo.app.models.ShopPricing;
import com.pressgo.app.repositories.PrinterRepository;
import com.pressgo.app.repositories.ShopRepository;

import java.util.Locale;

public class ShopDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ShopDetailsActivity";
    public  static final String EXTRA_SHOP_ID = "extra_shop_id";

    private ActivityShopDetailsBinding binding;
    private ShopRepository    shopRepository;
    private PrinterRepository printerRepository;
    private String shopId;
    private Shop currentShop;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityShopDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopRepository    = ShopRepository.getInstance();
        printerRepository = PrinterRepository.getInstance();

        shopId = getIntent().getStringExtra(EXTRA_SHOP_ID);
        if (shopId == null || shopId.isEmpty()) {
            shopId = getIntent().getStringExtra(com.pressgo.app.utils.Constants.EXTRA_SHOP_ID);
        }
        if (shopId == null || shopId.isEmpty()) {
            Toast.makeText(this, "Shop not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Shop Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupPrinterList();
        loadShopDetails();

        binding.btnStartOrder.setOnClickListener(v -> navigateToDocumentUpload());
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

    private void setupPrinterList() {
        PrinterAdapter printerAdapter = new PrinterAdapter();
        binding.rvPrinters.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPrinters.setAdapter(printerAdapter);
        binding.rvPrinters.setNestedScrollingEnabled(false);

        printerRepository.getActivePrintersForShop(shopId, printers -> {
            if (printers != null && !printers.isEmpty()) {
                printerAdapter.submitList(printers);
                binding.tvNoPrinters.setVisibility(View.GONE);
            } else {
                binding.tvNoPrinters.setVisibility(View.VISIBLE);
            }
        }, e -> {
            Log.e(TAG, "Failed to load printers", e);
            binding.tvNoPrinters.setVisibility(View.VISIBLE);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    private void loadShopDetails() {
        showLoading(true);

        shopRepository.getShopById(shopId, shop -> {
            if (shop == null) {
                showLoading(false);
                Toast.makeText(this, "Shop not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            bindShopDetails(shop);
            loadPricing(shop);
        }, e -> {
            Log.e(TAG, "Failed to load shop", e);
            showLoading(false);
            Toast.makeText(this, "Failed to load shop details.", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadPricing(Shop shop) {
        shopRepository.getShopPricing(shopId, pricing -> {
            showLoading(false);
            if (pricing != null) {
                bindPricing(pricing);
            }
        }, e -> {
            showLoading(false);
            Log.e(TAG, "Failed to load pricing", e);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Binding helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void bindShopDetails(Shop shop) {
        this.currentShop = shop;
        binding.tvShopName.setText(shop.getName() != null ? shop.getName() : "—");
        binding.tvShopAddress.setText(shop.getAddress() != null ? shop.getAddress() : "—");

        // Map location setup
        double lat = shop.getLatitude();
        double lng = shop.getLongitude();
        if (lat != 0.0 && lng != 0.0) {
            binding.tvMapCoordsPreview.setText(String.format(Locale.getDefault(), "Pinned on campus map (%.4f, %.4f)", lat, lng));
        } else {
            binding.tvMapCoordsPreview.setText(shop.getAddress() != null ? shop.getAddress() : "View campus location");
        }

        binding.btnViewOnCampusMap.setOnClickListener(v -> {
            Intent intent = new Intent(this, ShopMapActivity.class);
            intent.putExtra(ShopMapActivity.EXTRA_MODE, ShopMapActivity.MODE_VIEW);
            intent.putExtra(ShopMapActivity.EXTRA_LATITUDE, lat != 0.0 ? lat : 23.7808);
            intent.putExtra(ShopMapActivity.EXTRA_LONGITUDE, lng != 0.0 ? lng : 90.4192);
            intent.putExtra(ShopMapActivity.EXTRA_SHOP_NAME, shop.getName() != null ? shop.getName() : "Campus Print Shop");
            intent.putExtra(ShopMapActivity.EXTRA_SHOP_ADDRESS, shop.getAddress() != null ? shop.getAddress() : "");
            intent.putExtra(ShopMapActivity.EXTRA_SHOP_ID, shop.getShopId());
            startActivity(intent);
        });

        binding.btnGetDirections.setOnClickListener(v -> {
            double destLat = lat != 0.0 ? lat : 23.7808;
            double destLng = lng != 0.0 ? lng : 90.4192;
            String uriStr = "https://www.google.com/maps/dir/?api=1&destination=" + destLat + "," + destLng;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
            startActivity(intent);
        });

        // Phone with click-to-call
        String phone = shop.getPhone();
        if (phone != null && !phone.isEmpty()) {
            binding.tvShopPhone.setText(phone);
            binding.tvShopPhone.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + phone));
                startActivity(intent);
            });
        } else {
            binding.tvShopPhone.setText("—");
        }

        // Hours
        binding.tvShopHours.setText(shop.getHours() != null ? shop.getHours() : "—");

        // Open/closed badge
        if (shop.isOpen()) {
            binding.tvStatusBadge.setText("OPEN");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green);
        } else {
            binding.tvStatusBadge.setText("CLOSED");
            binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red);
        }

        // Rating
        if (shop.getRating() > 0) {
            binding.ratingBar.setRating((float) shop.getRating());
            binding.tvRating.setText(String.format(Locale.getDefault(), "%.1f", shop.getRating()));
        }

        // Services
        binding.chipColor.setVisibility(shop.supportsColor()   ? View.VISIBLE : View.GONE);
        binding.chipDuplex.setVisibility(shop.supportsDuplex() ? View.VISIBLE : View.GONE);
        binding.chipBinding.setVisibility(shop.supportsBinding()? View.VISIBLE : View.GONE);

        // Queue length
        binding.tvQueueLength.setText("Queue: " + shop.getQueueLength() + " orders");
        binding.tvEstWait.setText("Est. wait: ~" + shop.getEstimatedWaitMinutes() + " min");

        // Disable order button if shop is closed
        binding.btnStartOrder.setEnabled(shop.isOpen());
        if (!shop.isOpen()) {
            binding.btnStartOrder.setText("Shop is Closed");
        } else {
            binding.btnStartOrder.setText("Send File to Shopkeeper");
        }
    }

    private void bindPricing(ShopPricing pricing) {
        binding.tvBwRate.setText(String.format(Locale.getDefault(),
                "B&W: BDT %.2f / page", pricing.getBwPricePerPage()));
        binding.tvColorRate.setText(String.format(Locale.getDefault(),
                "Color: BDT %.2f / page", pricing.getColorPricePerPage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToDocumentUpload() {
        if (currentShop != null && !currentShop.isOpen()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Shop is Closed")
                    .setMessage("\"" + currentShop.getName() + "\" is currently closed and not accepting print jobs. Please check back when the shop is open or choose an open shop.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        Intent intent = new Intent(this, DocumentUploadActivity.class);
        intent.putExtra(com.pressgo.app.utils.Constants.EXTRA_SHOP_ID, shopId);
        startActivity(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI state helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.contentScroll.setVisibility(loading ? View.GONE : View.VISIBLE);
    }
}
