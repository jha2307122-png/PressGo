package com.pressgo.app.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.pressgo.app.R;
import com.pressgo.app.models.PrintOrder;

public class StatusBadgeView extends AppCompatTextView {

    public StatusBadgeView(@NonNull Context context) {
        super(context);
        init();
    }

    public StatusBadgeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StatusBadgeView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int padH = (int) (12 * getResources().getDisplayMetrics().density);
        int padV = (int) (6 * getResources().getDisplayMetrics().density);
        setPadding(padH, padV, padH, padV);
        setTextSize(12f);
        setTextColor(Color.WHITE);
        setStatus(PrintOrder.STATUS_PENDING);
    }

    public void setStatus(String status) {
        if (status == null) status = PrintOrder.STATUS_PENDING;
        status = status.trim().toUpperCase(java.util.Locale.ROOT);
        setText(status.replace("_", " "));

        int color;
        switch (status) {
            case PrintOrder.STATUS_ACCEPTED:
                color = ContextCompat.getColor(getContext(), R.color.status_accepted);
                break;
            case PrintOrder.STATUS_PRINTING:
                color = ContextCompat.getColor(getContext(), R.color.status_printing);
                break;
            case PrintOrder.STATUS_READY_FOR_PICKUP:
                color = ContextCompat.getColor(getContext(), R.color.status_ready);
                break;
            case PrintOrder.STATUS_COMPLETED:
                color = ContextCompat.getColor(getContext(), R.color.status_completed);
                break;
            case PrintOrder.STATUS_REJECTED:
                color = ContextCompat.getColor(getContext(), R.color.status_rejected);
                break;
            case PrintOrder.STATUS_CANCELLED:
                color = ContextCompat.getColor(getContext(), R.color.status_cancelled);
                break;
            case PrintOrder.STATUS_PENDING:
            default:
                color = ContextCompat.getColor(getContext(), R.color.status_pending);
                break;
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(24 * getResources().getDisplayMetrics().density);
        setBackground(bg);
    }
}
