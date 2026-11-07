package com.pressgo.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver for low battery and battery status monitoring.
 * Monitors Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY,
 * Intent.ACTION_POWER_CONNECTED, and Intent.ACTION_POWER_DISCONNECTED.
 */
public class BatteryLevelReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryLevelReceiver";

    public interface OnBatteryStateChangedListener {
        void onBatteryChanged(boolean isLowBattery, boolean isCharging, int batteryPercent);
    }

    private OnBatteryStateChangedListener listener;

    public BatteryLevelReceiver() {}

    public BatteryLevelReceiver(OnBatteryStateChangedListener listener) {
        this.listener = listener;
    }

    public void setListener(OnBatteryStateChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Battery broadcast received: " + action);

        boolean isLow = Intent.ACTION_BATTERY_LOW.equals(action);
        boolean isCharging = Intent.ACTION_POWER_CONNECTED.equals(action);

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;

        if (listener != null) {
            listener.onBatteryChanged(isLow, isCharging, percent);
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        return filter;
    }

    public static BatteryLevelReceiver register(Context context, OnBatteryStateChangedListener listener) {
        if (context == null) return null;
        BatteryLevelReceiver receiver = new BatteryLevelReceiver(listener);
        try {
            Intent batteryStatus = ContextCompat.registerReceiver(
                    context,
                    receiver,
                    getIntentFilter(),
                    ContextCompat.RECEIVER_EXPORTED
            );
            if (batteryStatus != null && listener != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int percent = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == BatteryManager.BATTERY_STATUS_FULL;
                boolean isLow = percent >= 0 && percent <= 15;
                listener.onBatteryChanged(isLow, isCharging, percent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register BatteryLevelReceiver", e);
        }
        return receiver;
    }

    public static void safeUnregister(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) return;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering BatteryLevelReceiver", e);
        }
    }
}
