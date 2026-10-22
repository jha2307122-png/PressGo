package com.pressgo.app.utils;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.pressgo.app.R;
import com.pressgo.app.activities.AdminDashboardActivity;
import com.pressgo.app.activities.NotificationsActivity;
import com.pressgo.app.activities.OrderTrackingActivity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper methods for showing local notifications across PressGo.
 */
public final class NotificationUtils {

    private static final String TAG = "NotificationUtils";

    /** Auto-incrementing notification ID generator. */
    private static final AtomicInteger NOTIFICATION_ID_GEN = new AtomicInteger(1000);

    private NotificationUtils() {}

    /**
     * Ensures all required notification channels are registered in the OS.
     * Required for Android 8.0+ (API 26+) or notifications will be dropped silently.
     */
    public static void createNotificationChannels(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            Log.w(TAG, "createNotificationChannels: NotificationManager is null");
            return;
        }

        try {
            // ORDER_UPDATES channel
            NotificationChannel orderUpdates = new NotificationChannel(
                    Constants.CHANNEL_ORDER_UPDATES,
                    "Order Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            orderUpdates.setDescription("Receive real-time updates about your print order status.");
            orderUpdates.enableVibration(true);
            orderUpdates.enableLights(true);
            orderUpdates.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(orderUpdates);

            // ADMIN_ALERTS channel
            NotificationChannel adminAlerts = new NotificationChannel(
                    Constants.CHANNEL_ADMIN_ALERTS,
                    "Admin Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            adminAlerts.setDescription("Important alerts and new orders for print shop staff.");
            adminAlerts.enableVibration(true);
            adminAlerts.enableLights(true);
            adminAlerts.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(adminAlerts);

            // GENERAL channel
            NotificationChannel general = new NotificationChannel(
                    Constants.CHANNEL_GENERAL,
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            general.setDescription("General PressGo announcements and order notifications.");
            manager.createNotificationChannel(general);

            Log.d(TAG, "createNotificationChannels: Channels successfully verified/created");
        } catch (Exception e) {
            Log.e(TAG, "Error registering notification channels", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Order notifications
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a high-priority notification on the ORDER_UPDATES channel.
     *
     * @param context  Application / activity context
     * @param title    Notification title
     * @param message  Notification body text
     * @param orderId  Order ID used to build a deep-link intent (can be null)
     */
    public static void createOrderNotification(Context context,
                                                String title,
                                                String message,
                                                String orderId) {
        if (context == null) return;

        // Ensure channels are registered first
        createNotificationChannels(context);

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "createOrderNotification: POST_NOTIFICATIONS permission not granted");
            return;
        }

        // Target intent: Direct to OrderTrackingActivity if orderId is available, else NotificationsActivity
        Intent intent;
        if (orderId != null && !orderId.trim().isEmpty()) {
            intent = new Intent(context, OrderTrackingActivity.class);
            intent.putExtra(Constants.EXTRA_ORDER_ID, orderId.trim());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else {
            intent = new Intent(context, NotificationsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                   ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) (System.currentTimeMillis() & 0xfffffff),
                intent,
                flags
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Constants.CHANNEL_ORDER_UPDATES)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setSound(defaultSoundUri)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        showNotification(context, builder);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin alerts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a high-priority notification on the ADMIN_ALERTS channel.
     *
     * @param context Application / activity context
     * @param title   Notification title
     * @param message Notification body text
     */
    public static void createAdminAlert(Context context,
                                         String title,
                                         String message) {
        if (context == null) return;

        // Ensure channels are registered first
        createNotificationChannels(context);

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "createAdminAlert: POST_NOTIFICATIONS permission not granted");
            return;
        }

        Intent intent = new Intent(context, AdminDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                   ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) (System.currentTimeMillis() & 0xfffffff),
                intent,
                flags
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, Constants.CHANNEL_ADMIN_ALERTS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setSound(defaultSoundUri)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }

        showNotification(context, builder);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the app has the POST_NOTIFICATIONS permission
     * (required on Android 13+) or is running on an older OS version.
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // permission implicit on API < 33
    }

    /**
     * Requests the POST_NOTIFICATIONS runtime permission on Android 13+.
     * On older APIs this is a no-op.
     */
    public static void requestNotificationPermission(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                try {
                    ActivityCompat.requestPermissions(
                            activity,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            1001
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Failed to request notification permission", e);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void showNotification(Context context,
                                          NotificationCompat.Builder builder) {
        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.notify(NOTIFICATION_ID_GEN.getAndIncrement(), builder.build());
            Log.d(TAG, "showNotification: notification posted successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "showNotification: SecurityException – permission denied", e);
        } catch (Exception e) {
            Log.e(TAG, "showNotification: unexpected error", e);
        }
    }
}
