package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class Notification {

    // -------------------------------------------------------------------------
    // Type constants
    // -------------------------------------------------------------------------
    public static final String TYPE_ORDER_RECEIVED     = "ORDER_RECEIVED";
    public static final String TYPE_ORDER_ACCEPTED     = "ORDER_ACCEPTED";
    public static final String TYPE_ORDER_REJECTED     = "ORDER_REJECTED";
    public static final String TYPE_PRINTING_STARTED   = "PRINTING_STARTED";
    public static final String TYPE_READY_FOR_PICKUP   = "READY_FOR_PICKUP";
    public static final String TYPE_ORDER_COMPLETED    = "ORDER_COMPLETED";
    public static final String TYPE_ORDER_CANCELLED    = "ORDER_CANCELLED";
    public static final String TYPE_INVENTORY_ALERT    = "INVENTORY_ALERT";
    public static final String TYPE_GENERAL            = "GENERAL";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String userId;
    private String orderId;   // nullable
    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private Timestamp createdAt;
    private String imageUrl;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public Notification() {}

    public Notification(String id,
                        String userId,
                        String orderId,
                        String title,
                        String message,
                        String type,
                        boolean isRead,
                        Timestamp createdAt,
                        String imageUrl) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.isRead = isRead;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getUserId() { return userId; }

    public String getOrderId() { return orderId; }

    public String getTitle() { return title; }

    public String getMessage() { return message; }

    public String getType() { return type; }

    public boolean isRead() { return isRead; }

    public Timestamp getCreatedAt() { return createdAt; }

    public String getImageUrl() { return imageUrl; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setUserId(String userId) { this.userId = userId; }

    public void setOrderId(String orderId) { this.orderId = orderId; }

    public void setTitle(String title) { this.title = title; }

    public void setMessage(String message) { this.message = message; }

    public void setType(String type) { this.type = type; }

    public void setRead(boolean read) { isRead = read; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("orderId", orderId);
        map.put("title", title);
        map.put("message", message);
        map.put("type", type);
        map.put("isRead", isRead);
        map.put("createdAt", createdAt);
        map.put("imageUrl", imageUrl);
        return map;
    }
}
