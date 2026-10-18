package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class PrintOrder implements Serializable {

    // -------------------------------------------------------------------------
    // Order status constants
    // -------------------------------------------------------------------------
    public static final String ORDER_STATUS_PENDING           = "PENDING";
    public static final String ORDER_STATUS_ACCEPTED          = "ACCEPTED";
    public static final String ORDER_STATUS_PRINTING          = "PRINTING";
    public static final String ORDER_STATUS_READY_FOR_PICKUP  = "READY_FOR_PICKUP";
    public static final String ORDER_STATUS_COMPLETED         = "COMPLETED";
    public static final String ORDER_STATUS_REJECTED          = "REJECTED";
    public static final String ORDER_STATUS_CANCELLED         = "CANCELLED";

    // Aliases for compatibility
    public static final String STATUS_PENDING          = ORDER_STATUS_PENDING;
    public static final String STATUS_ACCEPTED         = ORDER_STATUS_ACCEPTED;
    public static final String STATUS_PRINTING         = ORDER_STATUS_PRINTING;
    public static final String STATUS_READY_FOR_PICKUP = ORDER_STATUS_READY_FOR_PICKUP;
    public static final String STATUS_COMPLETED        = ORDER_STATUS_COMPLETED;
    public static final String STATUS_REJECTED         = ORDER_STATUS_REJECTED;
    public static final String STATUS_CANCELLED        = ORDER_STATUS_CANCELLED;

    // -------------------------------------------------------------------------
    // Payment status constants
    // -------------------------------------------------------------------------
    public static final String PAYMENT_STATUS_UNPAID   = "UNPAID";
    public static final String PAYMENT_STATUS_PENDING  = "PENDING";
    public static final String PAYMENT_STATUS_PAID     = "PAID";
    public static final String PAYMENT_STATUS_FAILED   = "FAILED";
    public static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";

    public static final String PAYMENT_PAID            = PAYMENT_STATUS_PAID;
    public static final String PAYMENT_UNPAID          = PAYMENT_STATUS_UNPAID;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shopId;
    private String shopName;
    private String orderStatus;
    private String paymentStatus;
    private int totalPages;
    private int colorPages;
    private int bwPages;
    private int copies;
    private String paperSize;
    private boolean isBothSides;
    private boolean isColor;
    private boolean isPortrait;
    private boolean isPriority;
    private boolean hasBinding;
    private boolean hasStapling;
    private String pageRange;
    private String customerNotes;
    private double estimatedPrice;
    private double finalPrice;
    private Timestamp submittedAt;
    private Timestamp acceptedAt;
    private Timestamp printingStartedAt;
    private Timestamp readyAt;
    private Timestamp completedAt;
    private Timestamp estimatedCompletionTime;
    private int queuePosition;
    private String pickupToken;
    private String rejectionReason;
    private String cancellationReason;
    private String refundStatus;
    private String staffNotes;
    private String transactionRef;
    private String paymentMethod;
    private Map<String, Object> pricingSnapshot;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public PrintOrder() {}

    public PrintOrder(String id,
                      String customerId,
                      String customerName,
                      String customerEmail,
                      String shopId,
                      String shopName,
                      String orderStatus,
                      String paymentStatus,
                      int totalPages,
                      int colorPages,
                      int bwPages,
                      int copies,
                      String paperSize,
                      boolean isBothSides,
                      boolean isColor,
                      boolean isPortrait,
                      boolean isPriority,
                      boolean hasBinding,
                      boolean hasStapling,
                      String pageRange,
                      String customerNotes,
                      double estimatedPrice,
                      double finalPrice,
                      Timestamp submittedAt,
                      Timestamp acceptedAt,
                      Timestamp printingStartedAt,
                      Timestamp readyAt,
                      Timestamp completedAt,
                      Timestamp estimatedCompletionTime,
                      int queuePosition,
                      String pickupToken,
                      String rejectionReason,
                      String cancellationReason,
                      String refundStatus,
                      String staffNotes,
                      Map<String, Object> pricingSnapshot) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.shopId = shopId;
        this.shopName = shopName;
        this.orderStatus = orderStatus;
        this.paymentStatus = paymentStatus;
        this.totalPages = totalPages;
        this.colorPages = colorPages;
        this.bwPages = bwPages;
        this.copies = copies;
        this.paperSize = paperSize;
        this.isBothSides = isBothSides;
        this.isColor = isColor;
        this.isPortrait = isPortrait;
        this.isPriority = isPriority;
        this.hasBinding = hasBinding;
        this.hasStapling = hasStapling;
        this.pageRange = pageRange;
        this.customerNotes = customerNotes;
        this.estimatedPrice = estimatedPrice;
        this.finalPrice = finalPrice;
        this.submittedAt = submittedAt;
        this.acceptedAt = acceptedAt;
        this.printingStartedAt = printingStartedAt;
        this.readyAt = readyAt;
        this.completedAt = completedAt;
        this.estimatedCompletionTime = estimatedCompletionTime;
        this.queuePosition = queuePosition;
        this.pickupToken = pickupToken;
        this.rejectionReason = rejectionReason;
        this.cancellationReason = cancellationReason;
        this.refundStatus = refundStatus;
        this.staffNotes = staffNotes;
        this.pricingSnapshot = pricingSnapshot;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getCustomerId() { return customerId; }

    public String getCustomerName() { return customerName; }

    public String getCustomerEmail() { return customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getShopId() { return shopId; }

    public String getShopName() { return shopName; }

    public String getOrderStatus() { return orderStatus; }

    public String getPaymentStatus() { return paymentStatus; }

    public int getTotalPages() { return totalPages; }

    public int getColorPages() { return colorPages; }

    public int getBwPages() { return bwPages; }

    public int getCopies() { return copies; }

    public String getPaperSize() { return paperSize; }

    public boolean isBothSides() { return isBothSides; }

    public boolean isColor() { return isColor; }

    public boolean isPortrait() { return isPortrait; }

    public boolean isPriority() { return isPriority; }

    public boolean isHasBinding() { return hasBinding; }

    public boolean isHasStapling() { return hasStapling; }

    public String getPageRange() { return pageRange; }

    public String getCustomerNotes() { return customerNotes; }

    public double getEstimatedPrice() { return estimatedPrice; }

    public double getFinalPrice() { return finalPrice; }

    public Timestamp getSubmittedAt() { return submittedAt; }

    public Timestamp getAcceptedAt() { return acceptedAt; }

    public Timestamp getPrintingStartedAt() { return printingStartedAt; }

    public Timestamp getReadyAt() { return readyAt; }

    public Timestamp getCompletedAt() { return completedAt; }

    public Timestamp getEstimatedCompletionTime() { return estimatedCompletionTime; }

    public int getQueuePosition() { return queuePosition; }

    public String getPickupToken() { return pickupToken; }

    public String getRejectionReason() { return rejectionReason; }

    public String getCancellationReason() { return cancellationReason; }

    public String getRefundStatus() { return refundStatus; }

    public String getStaffNotes() { return staffNotes; }

    public String getTransactionRef() { return transactionRef; }

    public String getPaymentMethod() { return paymentMethod; }

    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public Map<String, Object> getPricingSnapshot() { return pricingSnapshot; }

    // --- Convenience / Alias Getters & Setters ---
    @Exclude
    public String getOrderId() { return id != null ? id : ""; }
    @Exclude
    public void setOrderId(String orderId) { this.id = orderId; }

    @Exclude
    public String getStatus() { return orderStatus; }
    @Exclude
    public void setStatus(String status) {
        if (this.orderStatus == null || this.orderStatus.isEmpty()) {
            this.orderStatus = status;
        }
    }

    @Exclude
    public double getTotalAmount() { return finalPrice > 0 ? finalPrice : estimatedPrice; }

    @Exclude
    public Timestamp getCreatedAt() { return submittedAt; }
    @Exclude
    public void setCreatedAt(Timestamp createdAt) { this.submittedAt = createdAt; }

    @Exclude
    public boolean hasBinding() { return hasBinding; }
    @Exclude
    public boolean hasStapling() { return hasStapling; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setShopName(String shopName) { this.shopName = shopName; }

    public void setOrderStatus(String orderStatus) {
        if (orderStatus != null && !orderStatus.isEmpty()) {
            this.orderStatus = orderStatus;
        }
    }

    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public void setColorPages(int colorPages) { this.colorPages = colorPages; }

    public void setBwPages(int bwPages) { this.bwPages = bwPages; }

    public void setCopies(int copies) { this.copies = copies; }

    public void setPaperSize(String paperSize) { this.paperSize = paperSize; }

    public void setBothSides(boolean bothSides) { isBothSides = bothSides; }

    public void setColor(boolean color) { isColor = color; }

    public void setPortrait(boolean portrait) { isPortrait = portrait; }

    public void setPriority(boolean priority) { isPriority = priority; }

    public void setHasBinding(boolean hasBinding) { this.hasBinding = hasBinding; }

    public void setHasStapling(boolean hasStapling) { this.hasStapling = hasStapling; }

    public void setPageRange(String pageRange) { this.pageRange = pageRange; }

    public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }

    public void setEstimatedPrice(double estimatedPrice) { this.estimatedPrice = estimatedPrice; }

    public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }

    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }

    public void setAcceptedAt(Timestamp acceptedAt) { this.acceptedAt = acceptedAt; }

    public void setPrintingStartedAt(Timestamp printingStartedAt) { this.printingStartedAt = printingStartedAt; }

    public void setReadyAt(Timestamp readyAt) { this.readyAt = readyAt; }

    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    public void setEstimatedCompletionTime(Timestamp estimatedCompletionTime) { this.estimatedCompletionTime = estimatedCompletionTime; }

    public void setQueuePosition(int queuePosition) { this.queuePosition = queuePosition; }

    public void setPickupToken(String pickupToken) { this.pickupToken = pickupToken; }

    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public void setStaffNotes(String staffNotes) { this.staffNotes = staffNotes; }

    public void setPricingSnapshot(Map<String, Object> pricingSnapshot) { this.pricingSnapshot = pricingSnapshot; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("customerId", customerId);
        map.put("customerName", customerName);
        map.put("customerEmail", customerEmail);
        map.put("shopId", shopId);
        map.put("shopName", shopName);
        map.put("orderStatus", orderStatus);
        map.put("status", orderStatus);
        map.put("paymentStatus", paymentStatus);
        map.put("totalPages", totalPages);
        map.put("colorPages", colorPages);
        map.put("bwPages", bwPages);
        map.put("copies", copies);
        map.put("paperSize", paperSize);
        map.put("isBothSides", isBothSides);
        map.put("isColor", isColor);
        map.put("isPortrait", isPortrait);
        map.put("isPriority", isPriority);
        map.put("hasBinding", hasBinding);
        map.put("hasStapling", hasStapling);
        map.put("pageRange", pageRange);
        map.put("customerNotes", customerNotes);
        map.put("estimatedPrice", estimatedPrice);
        map.put("finalPrice", finalPrice);
        map.put("submittedAt", submittedAt);
        map.put("acceptedAt", acceptedAt);
        map.put("printingStartedAt", printingStartedAt);
        map.put("readyAt", readyAt);
        map.put("completedAt", completedAt);
        map.put("estimatedCompletionTime", estimatedCompletionTime);
        map.put("queuePosition", queuePosition);
        map.put("pickupToken", pickupToken);
        map.put("rejectionReason", rejectionReason);
        map.put("cancellationReason", cancellationReason);
        map.put("refundStatus", refundStatus);
        map.put("staffNotes", staffNotes);
        map.put("transactionRef", transactionRef);
        map.put("paymentMethod", paymentMethod);
        map.put("pricingSnapshot", pricingSnapshot);
        return map;
    }
}
