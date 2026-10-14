package com.pressgo.app.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Systematic Activity & Process Lifecycle Observer.
 * Demonstrates systematic observation, tracking, and logging of Android lifecycle callbacks.
 * (Week 8: Activity Lifecycle Observation).
 */
public class AppLifecycleObserver implements DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycle";
    private static int activeActivityCount = 0;

    // ─────────────────────────────────────────────────────────────────────────
    // Process-level lifecycle (DefaultLifecycleObserver)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        Log.i(TAG, ">> ProcessLifecycle: App entered FOREGROUND");
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        Log.i(TAG, ">> ProcessLifecycle: App entered BACKGROUND");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Systematic Activity-level lifecycle callbacks (ActivityLifecycleCallbacks)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "[LIFECYCLE: onCreate] -> " + activity.getClass().getSimpleName()
                + (savedInstanceState != null ? " (Restoring saved state)" : " (New launch)"));
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        activeActivityCount++;
        Log.d(TAG, "[LIFECYCLE: onStart] -> " + activity.getClass().getSimpleName()
                + " (Active activities: " + activeActivityCount + ")");
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "[LIFECYCLE: onResume] -> " + activity.getClass().getSimpleName() + " (Visible & Interactive)");
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        Log.d(TAG, "[LIFECYCLE: onPause] -> " + activity.getClass().getSimpleName() + " (Partially visible/losing focus)");
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        activeActivityCount = Math.max(0, activeActivityCount - 1);
        Log.d(TAG, "[LIFECYCLE: onStop] -> " + activity.getClass().getSimpleName() + " (Hidden/Stopped)");
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        Log.d(TAG, "[LIFECYCLE: onSaveInstanceState] -> " + activity.getClass().getSimpleName());
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Log.d(TAG, "[LIFECYCLE: onDestroy] -> " + activity.getClass().getSimpleName()
                + (activity.isFinishing() ? " (Finishing permanently)" : " (Configuration change/recreation)"));
    }

    public static boolean isAppInForeground() {
        return activeActivityCount > 0;
    }
}
