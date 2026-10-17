package com.pressgo.app.models;

import java.io.Serializable;

/**
 * Local calculation model for displaying a price breakdown to the user.
 * Not persisted to Firestore.
 */
public class PriceBreakdown implements Serializable {

    private int totalPages;
    private int colorPages;
    private int bwPages;
    private int copies;
    private double bwCharge;
    private double colorCharge;
    private double paperSizeMultiplier;
    private double duplexDiscount;
    private double bindingCharge;
    private double staplingCharge;
    private double priorityCharge;
    private double discounts;
    private double subtotal;
    private double estimatedTotal;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public PriceBreakdown() {}

    public PriceBreakdown(int totalPages,
                          int colorPages,
                          int bwPages,
                          int copies,
                          double bwCharge,
                          double colorCharge,
                          double paperSizeMultiplier,
                          double duplexDiscount,
                          double bindingCharge,
                          double staplingCharge,
                          double priorityCharge,
                          double discounts,
                          double subtotal,
                          double estimatedTotal) {
        this.totalPages = totalPages;
        this.colorPages = colorPages;
        this.bwPages = bwPages;
        this.copies = copies;
        this.bwCharge = bwCharge;
        this.colorCharge = colorCharge;
        this.paperSizeMultiplier = paperSizeMultiplier;
        this.duplexDiscount = duplexDiscount;
        this.bindingCharge = bindingCharge;
        this.staplingCharge = staplingCharge;
        this.priorityCharge = priorityCharge;
        this.discounts = discounts;
        this.subtotal = subtotal;
        this.estimatedTotal = estimatedTotal;
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /**
     * Calculates and sets {@link #estimatedTotal} by summing all charges and
     * subtracting discounts.  Call this after populating all charge fields.
     *
     * @return the calculated estimated total
     */
    public double calculateTotal() {
        subtotal = (bwCharge + colorCharge) * paperSizeMultiplier * copies;
        estimatedTotal = subtotal
                + bindingCharge
                + staplingCharge
                + priorityCharge
                - duplexDiscount
                - discounts;
        if (estimatedTotal < 0) {
            estimatedTotal = 0;
        }
        return estimatedTotal;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getTotalPages() { return totalPages; }

    public int getColorPages() { return colorPages; }

    public int getBwPages() { return bwPages; }

    public int getCopies() { return copies; }

    public double getBwCharge() { return bwCharge; }

    public double getColorCharge() { return colorCharge; }

    public double getPaperSizeMultiplier() { return paperSizeMultiplier; }

    public double getDuplexDiscount() { return duplexDiscount; }

    public double getBindingCharge() { return bindingCharge; }

    public double getStaplingCharge() { return staplingCharge; }

    public double getPriorityCharge() { return priorityCharge; }

    public double getDiscounts() { return discounts; }

    public double getSubtotal() { return subtotal; }

    public double getEstimatedTotal() { return estimatedTotal; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public void setColorPages(int colorPages) { this.colorPages = colorPages; }

    public void setBwPages(int bwPages) { this.bwPages = bwPages; }

    public void setCopies(int copies) { this.copies = copies; }

    public void setBwCharge(double bwCharge) { this.bwCharge = bwCharge; }

    public void setColorCharge(double colorCharge) { this.colorCharge = colorCharge; }

    public void setPaperSizeMultiplier(double paperSizeMultiplier) { this.paperSizeMultiplier = paperSizeMultiplier; }

    public void setDuplexDiscount(double duplexDiscount) { this.duplexDiscount = duplexDiscount; }

    public void setBindingCharge(double bindingCharge) { this.bindingCharge = bindingCharge; }

    public void setStaplingCharge(double staplingCharge) { this.staplingCharge = staplingCharge; }

    public void setPriorityCharge(double priorityCharge) { this.priorityCharge = priorityCharge; }

    public void setDiscounts(double discounts) { this.discounts = discounts; }

    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public void setEstimatedTotal(double estimatedTotal) { this.estimatedTotal = estimatedTotal; }
}
