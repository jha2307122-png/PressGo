package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

public class Payment {

    // -------------------------------------------------------------------------
    // Method constants
    // -------------------------------------------------------------------------
    public static final String METHOD_CASH   = "CASH";
    public static final String METHOD_WALLET = "WALLET";
    public static final String METHOD_ONLINE = "ONLINE";
    public static final String METHOD_BKASH  = "BKASH";
    public static final String METHOD_NAGAD  = "NAGAD";
    public static final String METHOD_CARD   = "CARD";

    // -------------------------------------------------------------------------
    // Status constants
    // -------------------------------------------------------------------------
    public static final String STATUS_UNPAID   = "UNPAID";
    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_PAID     = "PAID";
    public static final String STATUS_FAILED   = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @DocumentId
    private String id;
    private String orderId;
    private String customerId;
    private String shopId;
    private double amount;
    private String method;
    private String status;
    private String transactionRef;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private double refundAmount;
    private String refundReason;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for Firestore deserialization. */
    public Payment() {}

    public Payment(String id,
                   String orderId,
                   String customerId,
                   String shopId,
                   double amount,
                   String method,
                   String status,
                   String transactionRef,
                   Timestamp createdAt,
                   Timestamp updatedAt,
                   double refundAmount,
                   String refundReason) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.shopId = shopId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionRef = transactionRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.refundAmount = refundAmount;
        this.refundReason = refundReason;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getId() { return id; }

    public String getOrderId() { return orderId; }

    public String getCustomerId() { return customerId; }

    public String getShopId() { return shopId; }

    public double getAmount() { return amount; }

    public String getMethod() { return method; }

    public String getStatus() { return status; }

    public String getTransactionRef() { return transactionRef; }

    public Timestamp getCreatedAt() { return createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }

    public double getRefundAmount() { return refundAmount; }

    public String getRefundReason() { return refundReason; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setOrderId(String orderId) { this.orderId = orderId; }

    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public void setShopId(String shopId) { this.shopId = shopId; }

    public void setAmount(double amount) { this.amount = amount; }

    public void setMethod(String method) { this.method = method; }

    public void setStatus(String status) { this.status = status; }

    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    // -------------------------------------------------------------------------
    // toMap
    // -------------------------------------------------------------------------

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", orderId);
        map.put("customerId", customerId);
        map.put("shopId", shopId);
        map.put("amount", amount);
        map.put("method", method);
        map.put("status", status);
        map.put("transactionRef", transactionRef);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("refundAmount", refundAmount);
        map.put("refundReason", refundReason);
        return map;
    }
}
