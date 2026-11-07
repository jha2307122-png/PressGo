package com.pressgo.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.pressgo.app.utils.NotificationUtils;

/**
 * BroadcastReceiver that responds to Intent.ACTION_AIRPLANE_MODE_CHANGED.
 * Detects when airplane mode is enabled or disabled, notifies active listeners,
 * and displays an informational status notification when connectivity is disrupted.
 *
 * Adheres to Android background execution restrictions (Android 8.0+ / API 26+)
 * by offering dynamic lifecycle-safe registration using ContextCompat.
 */
public class AirplaneModeReceiver extends BroadcastReceiver {

    private static final String TAG = "AirplaneModeReceiver";

    public interface OnAirplaneModeChangedListener {
        void onAirplaneModeChanged(boolean isAirplaneModeOn);
    }

    private OnAirplaneModeChangedListener listener;

    public AirplaneModeReceiver() {
        // Default constructor required for broadcast receiver architecture
    }

    public AirplaneModeReceiver(OnAirplaneModeChangedListener listener) {
        this.listener = listener;
    }

    public void setListener(OnAirplaneModeChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(intent.getAction())) {
            return;
        }

        boolean isAirplaneModeOn = isAirplaneModeActive(context, intent);
        Log.d(TAG, "Airplane mode state changed: isAirplaneModeOn=" + isAirplaneModeOn);

        if (isAirplaneModeOn) {
            NotificationUtils.createSystemAlert(
                    context,
                    "Airplane Mode Active",
                    "PressGo is offline. Print job submission and cloud synchronization will pause until connectivity is restored."
            );
        }

        if (listener != null) {
            listener.onAirplaneModeChanged(isAirplaneModeOn);
        }
    }

    /**
     * Determines if Airplane Mode is currently enabled by checking the intent extras
     * with fallback to Settings.Global.
     */
    public static boolean isAirplaneModeActive(Context context, Intent intent) {
        if (intent != null && intent.hasExtra("state")) {
            return intent.getBooleanExtra("state", false);
        }
        return isAirplaneModeActive(context);
    }

    /**
     * Checks the system settings directly to see if airplane mode is currently on.
     */
    public static boolean isAirplaneModeActive(Context context) {
        if (context == null) return false;
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON,
                    0
            ) != 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking airplane mode state", e);
            return false;
        }
    }

    /**
     * Returns a properly configured IntentFilter for airplane mode changes.
     */
    public static IntentFilter getIntentFilter() {
        return new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    /**
     * Registers an AirplaneModeReceiver safely with ContextCompat for the target SDK.
     * Android 13+ (API 33) requires ContextCompat.RECEIVER_EXPORTED for system broadcasts.
     *
     * @param context Host Context
     * @param listener Callback for state changes
     * @return Registered receiver instance
     */
    public static AirplaneModeReceiver register(Context context, OnAirplaneModeChangedListener listener) {
        if (context == null) return null;
        AirplaneModeReceiver receiver = new AirplaneModeReceiver(listener);
        try {
            ContextCompat.registerReceiver(
                    context,
                    receiver,
                    getIntentFilter(),
                    ContextCompat.RECEIVER_EXPORTED
            );
            // Immediately notify listener of current system state
            if (listener != null) {
                listener.onAirplaneModeChanged(isAirplaneModeActive(context));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register AirplaneModeReceiver", e);
        }
        return receiver;
    }

    /**
     * Safely unregisters the receiver to prevent memory leaks.
     */
    public static void safeUnregister(Context context, BroadcastReceiver receiver) {
        if (context == null || receiver == null) return;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver already unregistered or not registered
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering AirplaneModeReceiver", e);
        }
    }
}
