package com.pressgo.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.pressgo.app.utils.NetworkUtils;
import com.pressgo.app.workers.NotificationSyncWorker;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean connected = NetworkUtils.isConnected(context);
        Log.d(TAG, "Network state changed: connected=" + connected);

        if (connected) {
            // Trigger sync worker when network is restored
            try {
                WorkManager.getInstance(context).enqueue(
                        new OneTimeWorkRequest.Builder(NotificationSyncWorker.class).build()
                );
            } catch (Exception e) {
                Log.e(TAG, "Error enqueuing sync worker", e);
            }
        }
    }
}
