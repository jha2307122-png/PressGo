package com.pressgo.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ServerValue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time printer status model stored at /printerStatus/{printerId}.
 * Tracks live hardware telemetry, operational state, and supplies.
 */
@IgnoreExtraProperties
public class LivePrinterStatus implements Serializable {

    private String printerId;
    private String shopId;
    private String name;
    private String status; // AVAILABLE, BUSY, OUT_OF_SERVICE, MAINTENANCE
    private int paperLevel;
    private int inkLevel;
    private int tonerLevel;
    private String currentJobId;
    private Long updatedAt;

    /** Required no-argument constructor for Firebase Realtime Database deserialization. */
    public LivePrinterStatus() {}

    public LivePrinterStatus(String printerId,
                             String shopId,
                             String name,
                             String status,
                             int paperLevel,
                             int inkLevel,
                             int tonerLevel,
                             String currentJobId,
                             Long updatedAt) {
        this.printerId = printerId;
        this.shopId = shopId;
        this.name = name;
        this.status = status;
        this.paperLevel = paperLevel;
        this.inkLevel = inkLevel;
        this.tonerLevel = tonerLevel;
        this.currentJobId = currentJobId;
        this.updatedAt = updatedAt;
    }

    public String getPrinterId() {
        return printerId;
    }

    public void setPrinterId(String printerId) {
        this.printerId = printerId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPaperLevel() {
        return paperLevel;
    }

    public void setPaperLevel(int paperLevel) {
        this.paperLevel = paperLevel;
    }

    public int getInkLevel() {
        return inkLevel;
    }

    public void setInkLevel(int inkLevel) {
        this.inkLevel = inkLevel;
    }

    public int getTonerLevel() {
        return tonerLevel;
    }

    public void setTonerLevel(int tonerLevel) {
        this.tonerLevel = tonerLevel;
    }

    public String getCurrentJobId() {
        return currentJobId;
    }

    public void setCurrentJobId(String currentJobId) {
        this.currentJobId = currentJobId;
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
        map.put("printerId", printerId);
        map.put("shopId", shopId);
        map.put("name", name != null ? name : "");
        map.put("status", status != null ? status : Printer.STATUS_AVAILABLE);
        map.put("paperLevel", paperLevel);
        map.put("inkLevel", inkLevel);
        map.put("tonerLevel", tonerLevel);
        map.put("currentJobId", currentJobId != null ? currentJobId : "");
        map.put("updatedAt", ServerValue.TIMESTAMP);
        return map;
    }
}
