package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pressgo.app.databinding.ActivitySplashBinding;
import com.pressgo.app.utils.Constants;
import com.pressgo.app.utils.SessionManager;

/**
 * Splash screen activity.
 *
 * <p>Displayed for 2 seconds while the app checks whether the user is
 * already authenticated.  If so, it reads the user role (from the local
 * session cache first, then Firestore) and navigates to the appropriate
 * dashboard.  Otherwise it navigates to {@link LoginActivity}.
 *
 * <p>Theme: {@code @style/Theme.PressGo.Splash}
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG            = "SplashActivity";
    private static final long   SPLASH_DELAY   = 2000L; // 2 seconds

    private ActivitySplashBinding binding;
    private SessionManager        sessionManager;
    private final Handler         handler        = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding        = ActivitySplashBinding.inflate(getLayoutInflater());
        sessionManager = new SessionManager(this);

        setContentView(binding.getRoot());

        // Delay navigation by SPLASH_DELAY ms
        handler.postDelayed(this::checkAuthState, SPLASH_DELAY);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        // Cancel pending callbacks to prevent leaks
        handler.removeCallbacksAndMessages(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth / navigation logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks Firebase Auth state and routes to the appropriate screen.
     */
    private void checkAuthState() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Log.d(TAG, "checkAuthState: no authenticated user → LoginActivity");
            navigateToLogin();
            return;
        }

        Log.d(TAG, "checkAuthState: user authenticated uid=" + currentUser.getUid());

        // Fast path: use cached role from SharedPreferences
        String cachedRole = sessionManager.getUserRole();
        if (cachedRole != null && !cachedRole.isEmpty()) {
            Log.d(TAG, "checkAuthState: cached role=" + cachedRole);
            navigateToDashboard(cachedRole);
            return;
        }

        // Slow path: fetch role from Firestore
        fetchRoleFromFirestore(currentUser.getUid());
    }

    /**
     * Fetches the user document from Firestore to determine the role,
     * then updates the local session and navigates accordingly.
     */
    private void fetchRoleFromFirestore(String uid) {
        Log.d(TAG, "fetchRoleFromFirestore: uid=" + uid);

        FirebaseFirestore.getInstance()
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        String role   = snapshot.getString("role");
                        String shopId = snapshot.getString("shopId");
                        Log.d(TAG, "fetchRoleFromFirestore: role=" + role
                                + " shopId=" + shopId);

                        sessionManager.saveUserSession(uid, role, shopId);
                        navigateToDashboard(role);
                    } else {
                        // Firestore document missing – treat as unauthenticated
                        Log.w(TAG, "fetchRoleFromFirestore: user document not found");
                        FirebaseAuth.getInstance().signOut();
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "fetchRoleFromFirestore: Firestore error", e);
                    // If offline, fall back to cached session if available
                    String cachedRole = sessionManager.getUserRole();
                    if (cachedRole != null) {
                        navigateToDashboard(cachedRole);
                    } else {
                        navigateToLogin();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToDashboard(String role) {
        Intent intent;
        if (Constants.ROLE_ADMIN.equals(role) || Constants.ROLE_STAFF.equals(role)) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(this, CustomerDashboardActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
