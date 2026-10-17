package com.pressgo.app.activities;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.pressgo.app.databinding.ActivityPriceBreakdownBinding;
import com.pressgo.app.models.PriceBreakdown;

import java.util.Locale;

public class PriceBreakdownActivity extends AppCompatActivity {
    private static final String TAG = "PriceBreakdownActivity";

    private ActivityPriceBreakdownBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPriceBreakdownBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Price Breakdown");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        PriceBreakdown breakdown = (PriceBreakdown) getIntent().getSerializableExtra("breakdown");
        if (breakdown != null) {
            populateBreakdown(breakdown);
        }

        binding.btnGotIt.setOnClickListener(v -> finish());
    }

    private void populateBreakdown(PriceBreakdown b) {
        binding.priceSummaryView.clearItems();

        if (b.getBwPages() > 0) {
            binding.priceSummaryView.addLineItem(
                    String.format(Locale.US, "B&W Printing (%d pages × %d copies)", b.getBwPages(), b.getCopies()),
                    b.getBwCharge(),
                    false
            );
        }

        if (b.getColorPages() > 0) {
            binding.priceSummaryView.addLineItem(
                    String.format(Locale.US, "Color Printing (%d pages × %d copies)", b.getColorPages(), b.getCopies()),
                    b.getColorCharge(),
                    false
            );
        }

        if (b.getDuplexDiscount() > 0) {
            binding.priceSummaryView.addLineItem("Double-sided discount", b.getDuplexDiscount(), true);
        }

        if (b.getBindingCharge() > 0) {
            binding.priceSummaryView.addLineItem("Binding finishing", b.getBindingCharge(), false);
        }

        if (b.getStaplingCharge() > 0) {
            binding.priceSummaryView.addLineItem("Stapling finishing", b.getStaplingCharge(), false);
        }

        if (b.getPriorityCharge() > 0) {
            binding.priceSummaryView.addLineItem("Priority rush service", b.getPriorityCharge(), false);
        }

        binding.priceSummaryView.setTotal(b.getEstimatedTotal());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
