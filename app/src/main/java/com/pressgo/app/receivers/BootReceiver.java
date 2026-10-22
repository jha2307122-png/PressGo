package com.pressgo.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.pressgo.app.workers.CleanupWorker;
import com.pressgo.app.workers.NotificationSyncWorker;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted, rescheduling periodic workers");

            try {
                PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(
                        CleanupWorker.class, 24, TimeUnit.HOURS
                ).build();

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "PressGoCleanup",
                        ExistingPeriodicWorkPolicy.KEEP,
                        cleanupRequest
                );

                PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                        NotificationSyncWorker.class, 1, TimeUnit.HOURS
                ).build();

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "PressGoNotificationSync",
                        ExistingPeriodicWorkPolicy.KEEP,
                        syncRequest
                );
            } catch (Exception e) {
                Log.e(TAG, "Error scheduling workers on boot", e);
            }
        }
    }
}
