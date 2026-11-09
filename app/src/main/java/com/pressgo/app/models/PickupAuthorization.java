package com.pressgo.app.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;

/**
 * Domain model representing a third-party order pickup authorization.
 * Created when a customer delegates another campus member to collect their print order.
 */
public class PickupAuthorization implements Serializable {

    // Status Constants
    public static final String STATUS_PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String STATUS_VERIFIED             = "VERIFIED";
    public static final String STATUS_REJECTED             = "REJECTED";
    public static final String STATUS_REVOKED              = "REVOKED";

    @DocumentId
    private String authorizationId;
    private String orderId;
    private String customerUid;
    private String customerName;
    private String customerPhone;
    private String authorizedContactName;
    private String authorizedContactPhone;
    private String authorizationMessage;
    private String status;
    private String shopId;
    private String shopName;
    private String verifiedByAdminUid;
    private String adminNotes;

    @ServerTimestamp
    private Timestamp createdAt;

    @ServerTimestamp
    private Timestamp updatedAt;

    /**
     * Default no-arg constructor required for Firestore deserialization.
     */
    public PickupAuthorization() {
        this.status = STATUS_PENDING_VERIFICATION;
    }

    public PickupAuthorization(String authorizationId,
                               String orderId,
                               String customerUid,
                               String customerName,
                               String authorizedContactName,
                               String authorizedContactPhone,
                               String authorizationMessage,
                               String status) {
        this.authorizationId = authorizationId;
        this.orderId = orderId;
        this.customerUid = customerUid;
        this.customerName = customerName;
        this.authorizedContactName = authorizedContactName;
        this.authorizedContactPhone = authorizedContactPhone;
        this.authorizationMessage = authorizationMessage;
        this.status = status != null ? status : STATUS_PENDING_VERIFICATION;
        this.createdAt = Timestamp.now();
        this.updatedAt = Timestamp.now();
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerUid() {
        return customerUid;
    }

    public void setCustomerUid(String customerUid) {
        this.customerUid = customerUid;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getAuthorizedContactName() {
        return authorizedContactName;
    }

    public void setAuthorizedContactName(String authorizedContactName) {
        this.authorizedContactName = authorizedContactName;
    }

    public String getAuthorizedContactPhone() {
        return authorizedContactPhone;
    }

    public void setAuthorizedContactPhone(String authorizedContactPhone) {
        this.authorizedContactPhone = authorizedContactPhone;
    }

    public String getAuthorizationMessage() {
        return authorizationMessage;
    }

    public void setAuthorizationMessage(String authorizationMessage) {
        this.authorizationMessage = authorizationMessage;
    }

    public String getStatus() {
        return status != null ? status : STATUS_PENDING_VERIFICATION;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getVerifiedByAdminUid() {
        return verifiedByAdminUid;
    }

    public void setVerifiedByAdminUid(String verifiedByAdminUid) {
        this.verifiedByAdminUid = verifiedByAdminUid;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPending() {
        return STATUS_PENDING_VERIFICATION.equals(status);
    }

    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equals(status);
    }

    public boolean isRevoked() {
        return STATUS_REVOKED.equals(status);
    }
}
