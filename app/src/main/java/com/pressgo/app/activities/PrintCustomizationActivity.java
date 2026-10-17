package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pressgo.app.R;
import com.pressgo.app.databinding.ActivityPrintCustomizationBinding;
import com.pressgo.app.models.PriceBreakdown;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.SelectedDocument;
import com.pressgo.app.models.Shop;
import com.pressgo.app.models.ShopPricing;
import com.pressgo.app.repositories.ShopRepository;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.PriceCalculator;
import com.pressgo.app.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Locale;

public class PrintCustomizationActivity extends AppCompatActivity {
    private static final String TAG = "PrintCustomization";

    private ActivityPrintCustomizationBinding binding;
    private String shopId;
    private ArrayList<SelectedDocument> documents;
    private Shop shop;
    private ShopPricing pricing;
    private PriceBreakdown currentBreakdown;
    private int totalDocumentPages = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPrintCustomizationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopId = getIntent().getStringExtra(Constants.EXTRA_SHOP_ID);
        documents = getIntent().getParcelableArrayListExtra("documents");
        if (documents == null) {
            try {
                documents = (ArrayList<SelectedDocument>) getIntent().getSerializableExtra("documents");
            } catch (Exception ignored) {}
        }

        if (documents == null || documents.isEmpty()) {
            Toast.makeText(this, "No documents provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        for (SelectedDocument doc : documents) {
            if (doc != null) {
                totalDocumentPages += Math.max(1, doc.getPageCount());
            }
        }
        totalDocumentPages = Math.max(1, totalDocumentPages);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Print Customization");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupSpinners();
        setupListeners();
        loadShopAndPricing();
    }

    private void setupSpinners() {
        String[] paperSizes = {"A4", "A3", "Letter"};
        ArrayAdapter<String> paperAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected_dark, paperSizes);
        paperAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_dark);
        binding.spinnerPaperSize.setAdapter(paperAdapter);

        String[] colorModes = {"Black & White", "Color"};
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected_dark, colorModes);
        colorAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_dark);
        binding.spinnerColorMode.setAdapter(colorAdapter);
    }

    private void loadShopAndPricing() {
        binding.progressBar.setVisibility(View.VISIBLE);
        if (shopId == null || shopId.trim().isEmpty()) {
            binding.progressBar.setVisibility(View.GONE);
            pricing = createDefaultPricing("default_shop");
            updateShopFeatures();
            recalculatePrice();
            return;
        }
        ShopRepository.getInstance().getShopById(shopId, s -> {
            shop = s;
            ShopRepository.getInstance().getShopPricing(shopId, p -> {
                binding.progressBar.setVisibility(View.GONE);
                pricing = p;
                if (pricing == null) {
                    pricing = createDefaultPricing(shopId);
                }
                updateShopFeatures();
                recalculatePrice();
            }, e -> {
                binding.progressBar.setVisibility(View.GONE);
                pricing = createDefaultPricing(shopId);
                recalculatePrice();
            });
        }, e -> {
            binding.progressBar.setVisibility(View.GONE);
            pricing = createDefaultPricing(shopId);
            recalculatePrice();
        });
    }

    private ShopPricing createDefaultPricing(String shopId) {
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

    private void updateShopFeatures() {
        if (pricing != null) {
            if (binding.rbBwPrint != null) {
                binding.rbBwPrint.setText(String.format(Locale.US, "Black & White (B&W) — BDT %.2f / page", pricing.getBwPageRate()));
            }
            if (binding.rbColorPrint != null) {
                binding.rbColorPrint.setText(String.format(Locale.US, "Colour Print — BDT %.2f / page", pricing.getColorPageRate()));
            }
            if (binding.rbDoubleSided != null) {
                double discount = pricing.getDuplexDiscount();
                if (discount > 0) {
                    binding.rbDoubleSided.setText(String.format(Locale.US, "Both sides / Double-sided (Save BDT %.2f / sheet)", discount));
                } else {
                    binding.rbDoubleSided.setText("Both sides / Double-sided (Duplex)");
                }
            }
            if (binding.rbSpiralBinding != null) {
                binding.rbSpiralBinding.setText(String.format(Locale.US, "Spiral / Thesis Binding (+BDT %.2f)", pricing.getBindingCharge()));
            }
            if (binding.rbStapling != null) {
                binding.rbStapling.setText(String.format(Locale.US, "Corner Stapling (+BDT %.2f)", pricing.getStaplingCharge()));
            }
        }
        if (shop != null) {
            if (binding.rbSpiralBinding != null) {
                binding.rbSpiralBinding.setEnabled(shop.isHasBinding());
            }
            if (binding.rbStapling != null) {
                binding.rbStapling.setEnabled(shop.isHasStapling());
            }
            if (!shop.isHasColorPrinting()) {
                if (binding.rbColorPrint != null) {
                    binding.rbColorPrint.setEnabled(false);
                    binding.rbColorPrint.setText("Colour Print (Not supported by this shop)");
                }
                if (binding.rbBwPrint != null) binding.rbBwPrint.setChecked(true);
            }
        }
    }

    private void setupListeners() {
        binding.btnCopiesMinus.setOnClickListener(v -> {
            int copies = getCopies();
            if (copies > 1) {
                binding.etCopies.setText(String.valueOf(copies - 1));
                recalculatePrice();
            }
        });

        binding.btnCopiesPlus.setOnClickListener(v -> {
            int copies = getCopies();
            binding.etCopies.setText(String.valueOf(copies + 1));
            recalculatePrice();
        });

        binding.etCopies.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    String original = s.toString();
                    String clean = original.replaceAll("[^0-9]", "");
                    if (!original.equals(clean)) {
                        binding.etCopies.setText(clean);
                        binding.etCopies.setSelection(clean.length());
                        return;
                    }
                }
                recalculatePrice();
            }
        });

        binding.etPageRange.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                recalculatePrice();
            }
        });

        binding.spinnerPaperSize.setOnItemSelectedListener(new SimpleItemListener(this::recalculatePrice));

        if (binding.rgColorMode != null) {
            binding.rgColorMode.setOnCheckedChangeListener((group, checkedId) -> recalculatePrice());
        }
        binding.rgSides.setOnCheckedChangeListener((group, checkedId) -> recalculatePrice());

        if (binding.rgBindingOption != null) {
            binding.rgBindingOption.setOnCheckedChangeListener((group, checkedId) -> recalculatePrice());
        }

        binding.cbPriority.setOnCheckedChangeListener((buttonView, isChecked) -> recalculatePrice());

        binding.btnViewBreakdown.setOnClickListener(v -> {
            if (currentBreakdown != null) {
                Intent intent = new Intent(this, PriceBreakdownActivity.class);
                intent.putExtra("breakdown", currentBreakdown);
                startActivity(intent);
            }
        });

        binding.btnProceedToConfirm.setOnClickListener(v -> showPrintConfirmationDialog());
    }

    private int getCopies() {
        try {
            String text = binding.etCopies.getText() != null
                    ? binding.etCopies.getText().toString().replaceAll("[^0-9]", "")
                    : "1";
            if (text.isEmpty()) return 1;
            int val = Integer.parseInt(text);
            return Math.max(val, 1);
        } catch (Exception e) {
            return 1;
        }
    }

    private void recalculatePrice() {
        if (pricing == null) return;

        PrintOrder draftOrder = buildOrderFromUI();
        currentBreakdown = PriceCalculator.calculateEstimatedPrice(draftOrder, pricing);

        binding.tvEstimatedTotal.setText(String.format(Locale.US, "BDT %.2f", currentBreakdown.getEstimatedTotal()));

        // Check budget if entered
        String budgetStr = binding.etBudget.getText() != null ? binding.etBudget.getText().toString().trim() : "";
        if (!budgetStr.isEmpty()) {
            try {
                double budget = Double.parseDouble(budgetStr);
                if (currentBreakdown.getEstimatedTotal() > budget) {
                    binding.tvBudgetWarning.setVisibility(View.VISIBLE);
                    binding.tvBudgetWarning.setText(String.format(Locale.US, "Warning: Exceeds your budget of BDT %.2f", budget));
                } else {
                    binding.tvBudgetWarning.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {}
        } else {
            binding.tvBudgetWarning.setVisibility(View.GONE);
        }
    }

    private PrintOrder buildOrderFromUI() {
        PrintOrder order = new PrintOrder();
        order.setShopId(shopId != null ? shopId : (shop != null ? shop.getId() : "default_shop"));
        order.setCopies(getCopies());

        boolean isColor = binding.rbColorPrint != null && binding.rbColorPrint.isChecked();
        order.setColor(isColor);
        String paperSize = (binding.spinnerPaperSize != null && binding.spinnerPaperSize.getSelectedItem() != null)
                ? binding.spinnerPaperSize.getSelectedItem().toString()
                : "A4";
        order.setPaperSize(paperSize);
        order.setBothSides(binding.rbDoubleSided != null && binding.rbDoubleSided.isChecked());
        order.setPortrait(binding.rbPortrait != null && binding.rbPortrait.isChecked());

        boolean hasBinding = binding.rbSpiralBinding != null && binding.rbSpiralBinding.isChecked();
        boolean hasStapling = binding.rbStapling != null && binding.rbStapling.isChecked();
        order.setHasBinding(hasBinding);
        order.setHasStapling(hasStapling);
        order.setPriority(binding.cbPriority != null && binding.cbPriority.isChecked());

        String range = (binding.etPageRange != null && binding.etPageRange.getText() != null)
                ? binding.etPageRange.getText().toString().trim()
                : "all";
        order.setPageRange(range.isEmpty() ? "all" : range);
        order.setCustomerNotes((binding.etCustomerNotes != null && binding.etCustomerNotes.getText() != null)
                ? binding.etCustomerNotes.getText().toString().trim()
                : "");

        int effectivePages = totalDocumentPages;
        if (!"all".equalsIgnoreCase(range) && !range.isEmpty()) {
            if (PriceCalculator.validatePageRange(range, totalDocumentPages)) {
                effectivePages = PriceCalculator.parsePageRange(range, totalDocumentPages).size();
            }
        }
        order.setTotalPages(effectivePages);
        if (isColor) {
            order.setColorPages(effectivePages);
            order.setBwPages(0);
        } else {
            order.setColorPages(0);
            order.setBwPages(effectivePages);
        }

        return order;
    }

    private void showPrintConfirmationDialog() {
        String range = binding.etPageRange.getText() != null ? binding.etPageRange.getText().toString().trim() : "all";
        if (!range.isEmpty() && !"all".equalsIgnoreCase(range)) {
            if (!PriceCalculator.validatePageRange(range, totalDocumentPages)) {
                binding.tilPageRange.setError("Invalid page range. Expected: 1-5,7,9-12");
                return;
            } else {
                binding.tilPageRange.setError(null);
            }
        }

        PrintOrder order = buildOrderFromUI();
        if (currentBreakdown != null) {
            order.setEstimatedPrice(currentBreakdown.getEstimatedTotal());
        }

        String colorText = order.isColor() ? "Colour Print" : "Black & White (B&W)";
        String sidesText = order.isBothSides() ? "Both sides / Double-sided" : "Single-sided";
        String bindingText = order.isHasBinding() ? "Spiral / Thesis Binding" : (order.isHasStapling() ? "Corner Stapled" : "No Binding");
        double total = currentBreakdown != null ? currentBreakdown.getEstimatedTotal() : 0.0;

        String notes = order.getCustomerNotes();
        String notesText = (notes != null && !notes.trim().isEmpty())
                ? ("• Extra Requests / Comments: \"" + notes.trim() + "\"\n")
                : "";

        String msg = "Shop: " + (shop != null ? shop.getName() : "Campus Print Shop") + "\n\n" +
                "• Mode: " + colorText + "\n" +
                "• Sides: " + sidesText + "\n" +
                "• Binding: " + bindingText + "\n" +
                notesText +
                "• Copies: " + order.getCopies() + " (" + order.getTotalPages() + " pages)\n" +
                "• Calculated Rate: BDT " + String.format(Locale.US, "%.2f", total) + "\n\n" +
                "Do you want to send this print job to the shopkeeper?";

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Print Order")
                .setMessage(msg)
                .setPositiveButton("Yes, Send to Shopkeeper", (dialog, which) -> {
                    proceedToConfirmation();
                })
                .setNegativeButton("No, Change Options", null)
                .show();
    }

    private void proceedToConfirmation() {
        String range = binding.etPageRange.getText() != null ? binding.etPageRange.getText().toString().trim() : "all";
        if (!range.isEmpty() && !"all".equalsIgnoreCase(range)) {
            if (!PriceCalculator.validatePageRange(range, totalDocumentPages)) {
                binding.tilPageRange.setError("Invalid page range. Expected: 1-5,7,9-12");
                return;
            } else {
                binding.tilPageRange.setError(null);
            }
        }

        PrintOrder order = buildOrderFromUI();
        if (currentBreakdown != null) {
            order.setEstimatedPrice(currentBreakdown.getEstimatedTotal());
        }

        Intent intent = new Intent(this, OrderConfirmationActivity.class);
        intent.putExtra("order", order);
        intent.putParcelableArrayListExtra("documents", documents);
        intent.putExtra("documents", documents);
        intent.putExtra("breakdown", currentBreakdown);
        intent.putExtra("shopName", shop != null ? shop.getName() : "Campus Printing Shop");
        startActivity(intent);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
    }

    private static class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable onSelected;
        public SimpleItemListener(Runnable onSelected) { this.onSelected = onSelected; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { onSelected.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
