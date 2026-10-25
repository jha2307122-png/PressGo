package com.pressgo.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time order tracking model stored at /liveOrders/{orderId}.
 * Contains lightweight state required for real-time customer and admin synchronization.
 */
@IgnoreExtraProperties
public class LiveOrderStatus implements Serializable {

    private String orderId;
    private String orderStatus;
    private String customerId;
    private String shopId;
    private String staffNotes;
    private String pickupToken;
    private Long updatedAt;
    private Integer estimatedWaitMinutes;

    /** Required no-argument constructor for Firebase Realtime Database deserialization. */
    public LiveOrderStatus() {}

    public LiveOrderStatus(String orderId,
                           String orderStatus,
                           String customerId,
                           String shopId,
                           String staffNotes,
                           String pickupToken,
                           Long updatedAt,
                           Integer estimatedWaitMinutes) {
        this.orderId = orderId;
        this.orderStatus = orderStatus;
        this.customerId = customerId;
        this.shopId = shopId;
        this.staffNotes = staffNotes;
        this.pickupToken = pickupToken;
        this.updatedAt = updatedAt;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    // Getters and Setters

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getStaffNotes() {
        return staffNotes;
    }

    public void setStaffNotes(String staffNotes) {
        this.staffNotes = staffNotes;
    }

    public String getPickupToken() {
        return pickupToken;
    }

    public void setPickupToken(String pickupToken) {
        this.pickupToken = pickupToken;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    /**
     * Converts to Map with server timestamp for atomic writes.
     */
    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", orderId);
        map.put("orderStatus", orderStatus);
        map.put("status", orderStatus); // Alias for compatibility
        map.put("customerId", customerId);
        map.put("shopId", shopId);
        map.put("staffNotes", staffNotes != null ? staffNotes : "");
        map.put("pickupToken", pickupToken != null ? pickupToken : "");
        map.put("estimatedWaitMinutes", estimatedWaitMinutes != null ? estimatedWaitMinutes : 0);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        return map;
    }
}
