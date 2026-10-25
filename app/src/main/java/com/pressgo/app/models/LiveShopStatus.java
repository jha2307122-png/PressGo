package com.pressgo.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;
import com.google.firebase.database.ServerValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time shop status model stored at /shopStatus/{shopId}.
 * Tracks live open/close availability, queue length, and estimated wait times.
 */
@IgnoreExtraProperties
public class LiveShopStatus implements Serializable {

    private String shopId;
    private boolean isOpen = true;
    private int queueLength;
    private int estimatedWaitMinutes;
    private Long updatedAt;

    /** Required no-argument constructor for Firebase Realtime Database deserialization. */
    public LiveShopStatus() {}

    public LiveShopStatus(String shopId, boolean isOpen, int queueLength, int estimatedWaitMinutes, Long updatedAt) {
        this.shopId = shopId;
        this.isOpen = isOpen;
        this.queueLength = queueLength;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
        this.updatedAt = updatedAt;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    @PropertyName("isOpen")
    public boolean isOpen() {
        return isOpen;
    }

    @PropertyName("isOpen")
    public void setIsOpen(boolean open) {
        this.isOpen = open;
    }

    @PropertyName("open")
    public boolean getOpen() {
        return isOpen;
    }

    @PropertyName("open")
    public void setOpen(boolean open) {
        this.isOpen = open;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
        this.estimatedWaitMinutes = estimatedWaitMinutes;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Exclude
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("isOpen", isOpen);
        map.put("open", isOpen);
        map.put("queueLength", queueLength);
        map.put("estimatedWaitMinutes", estimatedWaitMinutes);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        return map;
    }
}
