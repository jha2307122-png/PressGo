package com.pressgo.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Network connectivity utility methods.
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Connectivity checks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the device has an active internet connection
     * (Wi-Fi, cellular, Ethernet, or VPN).
     */
    public static boolean isConnected(Context context) {
        return isNetworkAvailable(context);
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps == null) return false;

            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            // API < 23 fallback
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /**
     * Returns {@code true} if the active network transport is Wi-Fi.
     */
    public static boolean isWifiConnected(Context context) {
        if (context == null) return false;

        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps == null) return false;

            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            // API < 23 fallback
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null
                    && info.isConnected()
                    && info.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }
}
