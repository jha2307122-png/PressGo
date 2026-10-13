package com.pressgo.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages user session data using SharedPreferences.
 * Stores and retrieves UID, role, and shopId so the app
 * can restore navigation state without a network round-trip.
 */
public class SessionManager {

    private static final String PREF_NAME = "pressgo_session";

    // Keys
    private static final String KEY_USER_ID   = "user_id";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_SHOP_ID   = "shop_id";
    private static final String KEY_LOGGED_IN = "is_logged_in";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs  = context.getApplicationContext()
                        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Write
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists the authenticated user's session data.
     *
     * @param uid    Firebase Auth UID
     * @param role   One of {@link Constants#ROLE_CUSTOMER}, {@link Constants#ROLE_STAFF},
     *               or {@link Constants#ROLE_ADMIN}
     * @param shopId Firestore shop document ID, or {@code null} for customers
     */
    public void saveUserSession(String uid, String role, String shopId) {
        editor.putString(KEY_USER_ID, uid);
        editor.putString(KEY_USER_ROLE, role != null ? role : "");
        editor.putString(KEY_SHOP_ID, shopId != null ? shopId : "");
        editor.putBoolean(KEY_LOGGED_IN, true);
        editor.apply();
    }

    public void setShopId(String shopId) {
        editor.putString(KEY_SHOP_ID, shopId != null ? shopId : "");
        editor.apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @return Firebase Auth UID, or {@code null} if not logged in.
     */
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    /**
     * @return The stored user role string, or {@code null} if absent.
     */
    public String getUserRole() {
        String role = prefs.getString(KEY_USER_ROLE, null);
        return (role != null && !role.isEmpty()) ? role : null;
    }

    /**
     * @return The stored shop ID, or {@code null} if absent / not applicable.
     */
    public String getShopId() {
        String shopId = prefs.getString(KEY_SHOP_ID, null);
        return (shopId != null && !shopId.isEmpty()) ? shopId : null;
    }

    /**
     * @return {@code true} if a valid session exists.
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Removes all session data (called on sign-out).
     */
    public void clearSession() {
        editor.clear();
        editor.apply();
    }
}
