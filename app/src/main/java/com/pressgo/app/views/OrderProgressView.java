package com.pressgo.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.pressgo.app.R;
import com.pressgo.app.models.PrintOrder;

public class OrderProgressView extends View {

    private static final String[] STEPS = {"Pending", "Accepted", "Printing", "Ready", "Completed"};
    private static final String[] STEP_KEYS = {
            PrintOrder.STATUS_PENDING,
            PrintOrder.STATUS_ACCEPTED,
            PrintOrder.STATUS_PRINTING,
            PrintOrder.STATUS_READY_FOR_PICKUP,
            PrintOrder.STATUS_COMPLETED
    };

    private int currentStep = 0;
    private Paint linePaint;
    private Paint circlePaint;
    private Paint activeCirclePaint;
    private Paint textPaint;
    private float density;

    public OrderProgressView(Context context) {
        super(context);
        init();
    }

    public OrderProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OrderProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(3 * density);
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));

        activeCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activeCirclePaint.setStyle(Paint.Style.FILL);
        activeCirclePaint.setColor(ContextCompat.getColor(getContext(), R.color.primary));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(11 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
    }

    public void setCurrentStatus(String status) {
        if (status == null) {
            currentStep = 0;
        } else if (PrintOrder.STATUS_REJECTED.equalsIgnoreCase(status) || PrintOrder.STATUS_CANCELLED.equalsIgnoreCase(status)) {
            currentStep = -1;
        } else {
            for (int i = 0; i < STEP_KEYS.length; i++) {
                if (STEP_KEYS[i].equalsIgnoreCase(status)) {
                    currentStep = i;
                    break;
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) (65 * density);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int stepCount = STEPS.length;
        float padding = 32 * density;
        float availableWidth = width - (padding * 2);
        float stepSpacing = availableWidth / (stepCount - 1);
        float centerY = 20 * density;
        float radius = 10 * density;

        // Draw connecting lines
        for (int i = 0; i < stepCount - 1; i++) {
            float startX = padding + (i * stepSpacing);
            float endX = padding + ((i + 1) * stepSpacing);
            boolean isCompleted = i < currentStep;
            linePaint.setColor(isCompleted ? ContextCompat.getColor(getContext(), R.color.primary)
                    : ContextCompat.getColor(getContext(), R.color.divider));
            canvas.drawLine(startX, centerY, endX, centerY, linePaint);
        }

        // Draw circles and text
        for (int i = 0; i < stepCount; i++) {
            float cx = padding + (i * stepSpacing);
            boolean isActive = (i <= currentStep && currentStep >= 0);

            Paint paintToUse = isActive ? activeCirclePaint : circlePaint;
            canvas.drawCircle(cx, centerY, radius, paintToUse);

            // Draw center white dot for active
            if (isActive) {
                Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                dotPaint.setColor(ContextCompat.getColor(getContext(), R.color.white));
                canvas.drawCircle(cx, centerY, 4 * density, dotPaint);
            }

            textPaint.setColor(isActive ? ContextCompat.getColor(getContext(), R.color.primary)
                    : ContextCompat.getColor(getContext(), R.color.text_secondary));
            canvas.drawText(STEPS[i], cx, centerY + radius + (18 * density), textPaint);
        }
    }
}
