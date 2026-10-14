package com.pressgo.app.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.pressgo.app.R;

import java.util.Locale;

public class PriceSummaryView extends LinearLayout {

    private LinearLayout itemsContainer;
    private TextView tvTotalAmount;

    public PriceSummaryView(Context context) {
        super(context);
        init();
    }

    public PriceSummaryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PriceSummaryView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.surface));

        itemsContainer = new LinearLayout(getContext());
        itemsContainer.setOrientation(VERTICAL);
        addView(itemsContainer);

        // Divider
        LinearLayout divider = new LinearLayout(getContext());
        LayoutParams divParams = new LayoutParams(LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        divParams.setMargins(0, pad / 2, 0, pad / 2);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.divider));
        addView(divider);

        // Total Row
        LinearLayout totalRow = new LinearLayout(getContext());
        totalRow.setOrientation(HORIZONTAL);
        LayoutParams totalRowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        totalRow.setLayoutParams(totalRowParams);

        TextView tvTotalLabel = new TextView(getContext());
        tvTotalLabel.setText("Estimated Total");
        tvTotalLabel.setTextSize(16f);
        tvTotalLabel.setTypeface(null, Typeface.BOLD);
        tvTotalLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        totalRow.addView(tvTotalLabel, labelParams);

        tvTotalAmount = new TextView(getContext());
        tvTotalAmount.setText("BDT 0.00");
        tvTotalAmount.setTextSize(18f);
        tvTotalAmount.setTypeface(null, Typeface.BOLD);
        tvTotalAmount.setTextColor(ContextCompat.getColor(getContext(), R.color.primary));
        tvTotalAmount.setGravity(Gravity.END);
        totalRow.addView(tvTotalAmount);

        addView(totalRow);
    }

    public void clearItems() {
        itemsContainer.removeAllViews();
    }

    public void addLineItem(String label, double amount, boolean isDiscount) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, (int) (4 * getResources().getDisplayMetrics().density), 0, (int) (4 * getResources().getDisplayMetrics().density));
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(getContext());
        tvLabel.setText(label);
        tvLabel.setTextSize(14f);
        tvLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        LayoutParams labelParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(tvLabel, labelParams);

        TextView tvAmount = new TextView(getContext());
        if (isDiscount) {
            tvAmount.setText(String.format(Locale.US, "-BDT %.2f", Math.abs(amount)));
            tvAmount.setTextColor(ContextCompat.getColor(getContext(), R.color.status_ready));
        } else {
            tvAmount.setText(String.format(Locale.US, "BDT %.2f", amount));
            tvAmount.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        }
        tvAmount.setTextSize(14f);
        tvAmount.setGravity(Gravity.END);
        row.addView(tvAmount);

        itemsContainer.addView(row);
    }

    public void setTotal(double total) {
        tvTotalAmount.setText(String.format(Locale.US, "BDT %.2f", total));
    }
}
