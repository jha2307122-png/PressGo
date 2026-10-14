package com.pressgo.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.google.firebase.FirebaseApp;
import com.pressgo.app.utils.Constants;

/**
 * PressGo Application class.
 * Responsible for global initialization of Firebase, WorkManager,
 * and notification channels.
 */
public class PressGoApplication extends Application implements Configuration.Provider {

    private static final String TAG = "PressGoApp";

    private static PressGoApplication instance;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Application starting");

        instance = this;

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        Log.d(TAG, "onCreate: Firebase initialized");

        // Note: WorkManager is initialized on-demand via Configuration.Provider (getWorkManagerConfiguration)

        // Create notification channels (required for API 26+)
        createNotificationChannels();
        Log.d(TAG, "onCreate: Notification channels created");

        // Systematic Activity Lifecycle observation & logging (Week 8)
        com.pressgo.app.lifecycle.AppLifecycleObserver lifecycleObserver =
                new com.pressgo.app.lifecycle.AppLifecycleObserver();
        registerActivityLifecycleCallbacks(lifecycleObserver);
        Log.d(TAG, "onCreate: ActivityLifecycleCallbacks registered");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "onTerminate: Application terminating");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory: Low memory warning received");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory: level=" + level);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static accessor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the singleton Application instance.
     */
    public static PressGoApplication getInstance() {
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WorkManager configuration
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification channels
    // ─────────────────────────────────────────────────────────────────────────

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Channels are only required on API 26+
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            Log.w(TAG, "createNotificationChannels: NotificationManager is null");
            return;
        }

        // ORDER_UPDATES channel
        NotificationChannel orderUpdates = new NotificationChannel(
                Constants.CHANNEL_ORDER_UPDATES,
                "Order Updates",
                NotificationManager.IMPORTANCE_HIGH
        );
        orderUpdates.setDescription(
                "Receive real-time updates about your print order status.");
        orderUpdates.enableVibration(true);
        orderUpdates.enableLights(true);
        manager.createNotificationChannel(orderUpdates);
        Log.d(TAG, "createNotificationChannels: ORDER_UPDATES channel created");

        // ADMIN_ALERTS channel
        NotificationChannel adminAlerts = new NotificationChannel(
                Constants.CHANNEL_ADMIN_ALERTS,
                "Admin Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        adminAlerts.setDescription(
                "Important alerts for shop staff and administrators.");
        adminAlerts.enableVibration(true);
        adminAlerts.enableLights(true);
        manager.createNotificationChannel(adminAlerts);
        Log.d(TAG, "createNotificationChannels: ADMIN_ALERTS channel created");

        // GENERAL channel
        NotificationChannel general = new NotificationChannel(
                Constants.CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        general.setDescription(
                "General announcements and promotional messages.");
        manager.createNotificationChannel(general);
        Log.d(TAG, "createNotificationChannels: GENERAL channel created");
    }
}
