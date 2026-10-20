package com.pressgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pressgo.app.databinding.ActivityPricingManagementBinding;
import com.pressgo.app.models.ShopPricing;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;

import java.util.Locale;

public class PricingManagementActivity extends AppCompatActivity {
    private static final String TAG = "PricingManagement";

    private ActivityPricingManagementBinding binding;
    private String shopId;
    private ShopPricing pricing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPricingManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopId = getIntent().getStringExtra("shopId");
        if (shopId == null || shopId.isEmpty()) {
            shopId = getIntent().getStringExtra(Constants.EXTRA_SHOP_ID);
        }
        if (shopId == null || shopId.isEmpty()) {
            shopId = new SessionManager(this).getShopId();
        }
        if (shopId == null || shopId.isEmpty()) {
            shopId = "demo_shop_01";
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Shop Pricing & Rate Cards");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
        loadPricing();
    }

    private void setupListeners() {
        binding.btnSavePricing.setOnClickListener(v -> savePricing());
        binding.btnResetDefaults.setOnClickListener(v -> resetToDefaults());
    }

    private void loadPricing() {
        binding.progressBar.setVisibility(View.VISIBLE);
        ShopRepository.getInstance().getShopPricing(shopId, p -> {
            binding.progressBar.setVisibility(View.GONE);
            if (p != null) {
                pricing = p;
            } else {
                pricing = createDefaultPricing();
            }
            populateFields();
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            pricing = createDefaultPricing();
            populateFields();
        });
    }

    private ShopPricing createDefaultPricing() {
        ShopPricing sp = new ShopPricing();
        sp.setShopId(shopId);
        sp.setBwPageRate(0.10);
        sp.setColorPageRate(0.50);
        sp.setA4Multiplier(1.0);
        sp.setA3Multiplier(1.5);
        sp.setLetterMultiplier(1.0);
        sp.setDuplexDiscount(0.02);
        sp.setBindingCharge(2.50);
        sp.setStaplingCharge(0.50);
        sp.setPriorityCharge(3.00);
        return sp;
    }

    private void populateFields() {
        if (pricing == null) return;
        binding.etBwRate.setText(String.format(Locale.US, "%.2f", pricing.getBwPageRate()));
        binding.etColorRate.setText(String.format(Locale.US, "%.2f", pricing.getColorPageRate()));
        binding.etA4Multiplier.setText(String.format(Locale.US, "%.2f", pricing.getA4Multiplier()));
        binding.etA3Multiplier.setText(String.format(Locale.US, "%.2f", pricing.getA3Multiplier()));
        binding.etLetterMultiplier.setText(String.format(Locale.US, "%.2f", pricing.getLetterMultiplier()));
        binding.etDuplexDiscount.setText(String.format(Locale.US, "%.2f", pricing.getDuplexDiscount()));
        binding.etBindingCharge.setText(String.format(Locale.US, "%.2f", pricing.getBindingCharge()));
        binding.etStaplingCharge.setText(String.format(Locale.US, "%.2f", pricing.getStaplingCharge()));
        binding.etPriorityCharge.setText(String.format(Locale.US, "%.2f", pricing.getPriorityCharge()));
    }

    private void savePricing() {
        try {
            pricing.setBwPageRate(Double.parseDouble(binding.etBwRate.getText().toString().trim()));
            pricing.setColorPageRate(Double.parseDouble(binding.etColorRate.getText().toString().trim()));
            pricing.setA4Multiplier(Double.parseDouble(binding.etA4Multiplier.getText().toString().trim()));
            pricing.setA3Multiplier(Double.parseDouble(binding.etA3Multiplier.getText().toString().trim()));
            pricing.setLetterMultiplier(Double.parseDouble(binding.etLetterMultiplier.getText().toString().trim()));
            pricing.setDuplexDiscount(Double.parseDouble(binding.etDuplexDiscount.getText().toString().trim()));
            pricing.setBindingCharge(Double.parseDouble(binding.etBindingCharge.getText().toString().trim()));
            pricing.setStaplingCharge(Double.parseDouble(binding.etStaplingCharge.getText().toString().trim()));
            pricing.setPriorityCharge(Double.parseDouble(binding.etPriorityCharge.getText().toString().trim()));
            pricing.setUpdatedAt(Timestamp.now());

            binding.progressBar.setVisibility(View.VISIBLE);
            ShopRepository.getInstance().saveShopPricing(pricing, task -> {
                binding.progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Printing rates updated successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Rates updated locally!", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Please check all fields for valid numeric values", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetToDefaults() {
        pricing = createDefaultPricing();
        populateFields();
        Toast.makeText(this, "Reset to standard campus rates", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
