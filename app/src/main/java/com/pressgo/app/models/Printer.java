package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Printer {

    // -------------------------------------------------------------------------
    // Status constants
    // -------------------------------------------------------------------------
    public static final String STATUS_AVAILABLE    = "AVAILABLE";
    public static final String STATUS_BUSY         = "BUSY";
    public static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";
    public static final String STATUS_MAINTENANCE  = "MAINTENANCE";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String shopId;
    private String name;
    private String model;
    private String status;
    private List<String> supportedPaperSizes;
    private boolean hasColorSupport;
    private boolean hasDuplexSupport;
    private String currentJobId;
    private Timestamp lastUpdated;
    private int paperLevel;
    private int inkLevel;
    private int tonerLevel;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public Printer() {}

    public Printer(String id,
                   String shopId,
                   String name,
                   String model,
                   String status,
                   List<String> supportedPaperSizes,
                   boolean hasColorSupport,
                   boolean hasDuplexSupport,
                   String currentJobId,
                   Timestamp lastUpdated,
                   int paperLevel,
                   int inkLevel,
                   int tonerLevel) {
        this.id = id;
        this.shopId = shopId;
        this.name = name;
        this.model = model;
        this.status = status;
        this.supportedPaperSizes = supportedPaperSizes;
        this.hasColorSupport = hasColorSupport;
        this.hasDuplexSupport = hasDuplexSupport;
        this.currentJobId = currentJobId;
        this.lastUpdated = lastUpdated;
        this.paperLevel = paperLevel;
        this.inkLevel = inkLevel;
        this.tonerLevel = tonerLevel;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getShopId() { return shopId; }

    public String getName() { return name; }

    public String getModel() { return model; }

    public String getStatus() { return status; }

    public List<String> getSupportedPaperSizes() { return supportedPaperSizes; }

    public boolean isHasColorSupport() { return hasColorSupport; }

    public boolean isHasDuplexSupport() { return hasDuplexSupport; }

    public String getCurrentJobId() { return currentJobId; }

    public Timestamp getLastUpdated() { return lastUpdated; }

    public int getPaperLevel() { return paperLevel; }

    public int getInkLevel() { return inkLevel; }

    public int getTonerLevel() { return tonerLevel; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setName(String name) { this.name = name; }

    public void setModel(String model) { this.model = model; }

    public void setStatus(String status) { this.status = status; }

    public void setSupportedPaperSizes(List<String> supportedPaperSizes) { this.supportedPaperSizes = supportedPaperSizes; }

    public void setHasColorSupport(boolean hasColorSupport) { this.hasColorSupport = hasColorSupport; }

    public void setHasDuplexSupport(boolean hasDuplexSupport) { this.hasDuplexSupport = hasDuplexSupport; }

    public void setCurrentJobId(String currentJobId) { this.currentJobId = currentJobId; }

    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }

    public void setPaperLevel(int paperLevel) { this.paperLevel = paperLevel; }

    public void setInkLevel(int inkLevel) { this.inkLevel = inkLevel; }

    public void setTonerLevel(int tonerLevel) { this.tonerLevel = tonerLevel; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("name", name);
        map.put("model", model);
        map.put("status", status);
        map.put("supportedPaperSizes", supportedPaperSizes);
        map.put("hasColorSupport", hasColorSupport);
        map.put("hasDuplexSupport", hasDuplexSupport);
        map.put("currentJobId", currentJobId);
        map.put("lastUpdated", lastUpdated);
        map.put("paperLevel", paperLevel);
        map.put("inkLevel", inkLevel);
        map.put("tonerLevel", tonerLevel);
        return map;
    }
}
