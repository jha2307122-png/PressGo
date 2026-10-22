package com.pressgo.app.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pressgo.app.utils.DocumentUtils;

import java.io.File;

public class CleanupWorker extends Worker {
    private static final String TAG = "CleanupWorker";

    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            File tempDir = DocumentUtils.getTempDir(getApplicationContext());
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    long now = System.currentTimeMillis();
                    long oneDayMillis = 24 * 60 * 60 * 1000L;
                    for (File file : files) {
                        if (now - file.lastModified() > oneDayMillis) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "Deleted old temp file: " + file.getName() + " -> " + deleted);
                        }
                    }
                }
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning temp files", e);
            return Result.failure();
        }
    }
}
