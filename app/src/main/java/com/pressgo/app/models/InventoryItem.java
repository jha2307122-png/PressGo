package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class InventoryItem {

    // -------------------------------------------------------------------------
    // Category constants
    // -------------------------------------------------------------------------
    public static final String CATEGORY_PAPER            = "PAPER";
    public static final String CATEGORY_INK              = "INK";
    public static final String CATEGORY_TONER            = "TONER";
    public static final String CATEGORY_BINDING_MATERIAL = "BINDING_MATERIAL";
    public static final String CATEGORY_OTHER            = "OTHER";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String shopId;
    private String itemName;
    private String category;
    private int quantity;
    private String unit;
    private int lowStockThreshold;
    private boolean isLowStock;
    private Timestamp lastUpdated;
    private double cost;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public InventoryItem() {}

    public InventoryItem(String id,
                         String shopId,
                         String itemName,
                         String category,
                         int quantity,
                         String unit,
                         int lowStockThreshold,
                         boolean isLowStock,
                         Timestamp lastUpdated,
                         double cost) {
        this.id = id;
        this.shopId = shopId;
        this.itemName = itemName;
        this.category = category;
        this.quantity = quantity;
        this.unit = unit;
        this.lowStockThreshold = lowStockThreshold;
        this.isLowStock = isLowStock;
        this.lastUpdated = lastUpdated;
        this.cost = cost;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getShopId() { return shopId; }

    public String getItemName() { return itemName; }

    public String getCategory() { return category; }

    public int getQuantity() { return quantity; }

    public String getUnit() { return unit; }

    public int getLowStockThreshold() { return lowStockThreshold; }

    public boolean isLowStock() { return isLowStock; }

    public Timestamp getLastUpdated() { return lastUpdated; }

    public double getCost() { return cost; }

    // --- Convenience / Alias Getters & Setters ---
    public String getName() { return itemName != null ? itemName : ""; }
    public void setName(String name) { this.itemName = name; }
    public int getThreshold() { return lowStockThreshold; }
    public void setThreshold(int threshold) { this.lowStockThreshold = threshold; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setItemName(String itemName) { this.itemName = itemName; }

    public void setCategory(String category) { this.category = category; }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public void setUnit(String unit) { this.unit = unit; }

    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public void setLowStock(boolean lowStock) { isLowStock = lowStock; }

    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }

    public void setCost(double cost) { this.cost = cost; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("itemName", itemName);
        map.put("category", category);
        map.put("quantity", quantity);
        map.put("unit", unit);
        map.put("lowStockThreshold", lowStockThreshold);
        map.put("isLowStock", isLowStock);
        map.put("lastUpdated", lastUpdated);
        map.put("cost", cost);
        return map;
    }
}
