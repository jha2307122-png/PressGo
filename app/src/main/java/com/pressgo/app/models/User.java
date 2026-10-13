package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class User {

    // Role constants
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_STAFF    = "STAFF";
    public static final String ROLE_ADMIN    = "ADMIN";

    @DocumentId
    private String uid;
    private String email;
    private String displayName;
    private String phone;
    private String role;           // CUSTOMER, STAFF, ADMIN
    private String shopId;         // for STAFF / ADMIN
    private String profileImageUrl;
    private Timestamp createdAt;
    private boolean isActive;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public User() {}

    public User(String uid,
                String email,
                String displayName,
                String phone,
                String role,
                String shopId,
                String profileImageUrl,
                Timestamp createdAt,
                boolean isActive) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.phone = phone;
        this.role = role;
        this.shopId = shopId;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getUid() { return uid; }

    public String getEmail() { return email; }

    public String getDisplayName() { return displayName; }

    public String getPhone() { return phone; }

    public String getRole() { return role; }

    public String getShopId() { return shopId; }

    public String getProfileImageUrl() { return profileImageUrl; }

    public Timestamp getCreatedAt() { return createdAt; }

    public boolean isActive() { return isActive; }

    public String getName() { return displayName != null ? displayName : ""; }
    public void setName(String name) { this.displayName = name; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setUid(String uid) { this.uid = uid; }

    public void setEmail(String email) { this.email = email; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void setPhone(String phone) { this.phone = phone; }

    public void setRole(String role) { this.role = role; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setActive(boolean active) { isActive = active; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    /**
     * Returns a Firestore-compatible map representation of this user.
     * The @DocumentId field (uid) is intentionally excluded from the map
     * because Firestore manages the document ID separately.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("displayName", displayName);
        map.put("phone", phone);
        map.put("role", role);
        map.put("shopId", shopId);
        map.put("profileImageUrl", profileImageUrl);
        map.put("createdAt", createdAt);
        map.put("isActive", isActive);
        return map;
    }
}
