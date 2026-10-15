package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class ShopPricing {

    @DocumentId
    private String id;
    private String shopId;
    private double bwPageRate;
    private double colorPageRate;
    private double a4Multiplier;
    private double a3Multiplier;
    private double letterMultiplier;
    private double duplexDiscount;
    private double bindingCharge;
    private double staplingCharge;
    private double laminationCharge;
    private double priorityCharge;
    private double minimumOrder;
    private Timestamp updatedAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public ShopPricing() {}

    public ShopPricing(String id,
                       String shopId,
                       double bwPageRate,
                       double colorPageRate,
                       double a4Multiplier,
                       double a3Multiplier,
                       double letterMultiplier,
                       double duplexDiscount,
                       double bindingCharge,
                       double staplingCharge,
                       double laminationCharge,
                       double priorityCharge,
                       double minimumOrder,
                       Timestamp updatedAt) {
        this.id = id;
        this.shopId = shopId;
        this.bwPageRate = bwPageRate;
        this.colorPageRate = colorPageRate;
        this.a4Multiplier = a4Multiplier;
        this.a3Multiplier = a3Multiplier;
        this.letterMultiplier = letterMultiplier;
        this.duplexDiscount = duplexDiscount;
        this.bindingCharge = bindingCharge;
        this.staplingCharge = staplingCharge;
        this.laminationCharge = laminationCharge;
        this.priorityCharge = priorityCharge;
        this.minimumOrder = minimumOrder;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getShopId() { return shopId; }

    public double getBwPageRate() { return bwPageRate; }

    public double getColorPageRate() { return colorPageRate; }

    public double getA4Multiplier() { return a4Multiplier; }

    public double getA3Multiplier() { return a3Multiplier; }

    public double getLetterMultiplier() { return letterMultiplier; }

    public double getDuplexDiscount() { return duplexDiscount; }

    public double getBindingCharge() { return bindingCharge; }

    public double getStaplingCharge() { return staplingCharge; }

    public double getLaminationCharge() { return laminationCharge; }

    public double getPriorityCharge() { return priorityCharge; }

    public double getMinimumOrder() { return minimumOrder; }

    public Timestamp getUpdatedAt() { return updatedAt; }

    // --- Convenience / Alias Getters & Setters ---
    public double getBwPricePerPage() { return bwPageRate; }
    public void setBwPricePerPage(double bwPricePerPage) { this.bwPageRate = bwPricePerPage; }

    public double getColorPricePerPage() { return colorPageRate; }
    public void setColorPricePerPage(double colorPricePerPage) { this.colorPageRate = colorPricePerPage; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setBwPageRate(double bwPageRate) { this.bwPageRate = bwPageRate; }

    public void setColorPageRate(double colorPageRate) { this.colorPageRate = colorPageRate; }

    public void setA4Multiplier(double a4Multiplier) { this.a4Multiplier = a4Multiplier; }

    public void setA3Multiplier(double a3Multiplier) { this.a3Multiplier = a3Multiplier; }

    public void setLetterMultiplier(double letterMultiplier) { this.letterMultiplier = letterMultiplier; }

    public void setDuplexDiscount(double duplexDiscount) { this.duplexDiscount = duplexDiscount; }

    public void setBindingCharge(double bindingCharge) { this.bindingCharge = bindingCharge; }

    public void setStaplingCharge(double staplingCharge) { this.staplingCharge = staplingCharge; }

    public void setLaminationCharge(double laminationCharge) { this.laminationCharge = laminationCharge; }

    public void setPriorityCharge(double priorityCharge) { this.priorityCharge = priorityCharge; }

    public void setMinimumOrder(double minimumOrder) { this.minimumOrder = minimumOrder; }

    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("bwPageRate", bwPageRate);
        map.put("colorPageRate", colorPageRate);
        map.put("a4Multiplier", a4Multiplier);
        map.put("a3Multiplier", a3Multiplier);
        map.put("letterMultiplier", letterMultiplier);
        map.put("duplexDiscount", duplexDiscount);
        map.put("bindingCharge", bindingCharge);
        map.put("staplingCharge", staplingCharge);
        map.put("laminationCharge", laminationCharge);
        map.put("priorityCharge", priorityCharge);
        map.put("minimumOrder", minimumOrder);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
