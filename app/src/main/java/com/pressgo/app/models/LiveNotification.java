package com.pressgo.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time notification model stored at /liveNotifications/{userId}/{notificationId}.
 * Provides instant live notification dispatch and read acknowledgment.
 */
@IgnoreExtraProperties
public class LiveNotification implements Serializable {

    private String id;
    private String userId;
    private String orderId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private Long timestamp;

    /** Required no-argument constructor for Firebase Realtime Database deserialization. */
    public LiveNotification() {}

    public LiveNotification(String id,
                            String userId,
                            String orderId,
                            String title,
                            String message,
                            String type,
                            boolean read,
                            Long timestamp) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = read;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("orderId", orderId != null ? orderId : "");
        map.put("title", title != null ? title : "");
        map.put("message", message != null ? message : "");
        map.put("type", type != null ? type : Notification.TYPE_GENERAL);
        map.put("read", read);
        map.put("timestamp", ServerValue.TIMESTAMP);
        return map;
    }
}
