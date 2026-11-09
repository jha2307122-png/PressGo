package com.pressgo.app.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pressgo.app.models.PickupAuthorization;
import com.pressgo.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Firebase Firestore repository managing third-party order pickup authorizations.
 * Handles storage, duplicate active checks, status updates, and retrieval.
 */
public class PickupAuthorizationRepository {

    private static final String TAG = "PickupAuthRepo";
    private static PickupAuthorizationRepository instance;
    private final FirebaseFirestore db;

    private PickupAuthorizationRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized PickupAuthorizationRepository getInstance() {
        if (instance == null) {
            instance = new PickupAuthorizationRepository();
        }
        return instance;
    }

    /**
     * Creates or updates a pickup authorization for an order.
     * Prevents duplicate active authorizations by updating an existing record for the same order if present.
     */
    public void createOrUpdateAuthorization(PickupAuthorization auth,
                                            OnSuccessListener<String> onSuccess,
                                            OnFailureListener onFailure) {
        if (auth == null || auth.getOrderId() == null || auth.getOrderId().trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("Invalid authorization: orderId is required."));
            }
            return;
        }

        // Check if an authorization record for this order already exists
        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                .whereEqualTo("orderId", auth.getOrderId())
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        // Existing authorization found: update it safely to prevent duplicates
                        DocumentSnapshot existingDoc = queryDocumentSnapshots.getDocuments().get(0);
                        String docId = existingDoc.getId();
                        auth.setAuthorizationId(docId);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("authorizedContactName", auth.getAuthorizedContactName());
                        updates.put("authorizedContactPhone", auth.getAuthorizedContactPhone());
                        updates.put("authorizationMessage", auth.getAuthorizationMessage());
                        updates.put("status", PickupAuthorization.STATUS_PENDING_VERIFICATION);
                        updates.put("updatedAt", FieldValue.serverTimestamp());
                        if (auth.getCustomerName() != null) updates.put("customerName", auth.getCustomerName());
                        if (auth.getCustomerPhone() != null) updates.put("customerPhone", auth.getCustomerPhone());
                        if (auth.getShopId() != null) updates.put("shopId", auth.getShopId());
                        if (auth.getShopName() != null) updates.put("shopName", auth.getShopName());

                        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                                .document(docId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Updated existing pickup authorization: " + docId);
                                    if (onSuccess != null) {
                                        onSuccess.onSuccess(docId);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update pickup authorization", e);
                                    if (onFailure != null) {
                                        onFailure.onFailure(e);
                                    }
                                });
                    } else {
                        // New authorization record
                        String docId = auth.getAuthorizationId();
                        if (docId == null || docId.trim().isEmpty()) {
                            docId = UUID.randomUUID().toString();
                            auth.setAuthorizationId(docId);
                        }

                        auth.setStatus(PickupAuthorization.STATUS_PENDING_VERIFICATION);
                        auth.setCreatedAt(Timestamp.now());
                        auth.setUpdatedAt(Timestamp.now());

                        final String finalDocId = docId;
                        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                                .document(finalDocId)
                                .set(auth)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Created new pickup authorization: " + finalDocId);
                                    if (onSuccess != null) {
                                        onSuccess.onSuccess(finalDocId);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to create pickup authorization", e);
                                    if (onFailure != null) {
                                        onFailure.onFailure(e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed querying existing authorizations", e);
                    if (onFailure != null) {
                        onFailure.onFailure(e);
                    }
                });
    }

    /**
     * Retrieves the latest pickup authorization associated with a specific order.
     */
    public void getAuthorizationByOrderId(String orderId,
                                          OnSuccessListener<PickupAuthorization> onSuccess,
                                          OnFailureListener onFailure) {
        if (orderId == null || orderId.trim().isEmpty()) {
            if (onSuccess != null) onSuccess.onSuccess(null);
            return;
        }

        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                .whereEqualTo("orderId", orderId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        PickupAuthorization auth = doc.toObject(PickupAuthorization.class);
                        if (auth != null) {
                            auth.setAuthorizationId(doc.getId());
                        }
                        if (onSuccess != null) onSuccess.onSuccess(auth);
                    } else {
                        if (onSuccess != null) onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching authorization for order: " + orderId, e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    /**
     * Attaches a realtime listener to an order's pickup authorization.
     */
    public ListenerRegistration listenToAuthorizationByOrderId(String orderId,
                                                               EventListener<QuerySnapshot> listener) {
        return db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                .whereEqualTo("orderId", orderId)
                .limit(1)
                .addSnapshotListener(listener);
    }

    /**
     * Retrieves all authorizations created by a specific customer.
     */
    public void getAuthorizationsByCustomer(String customerUid,
                                            OnSuccessListener<List<PickupAuthorization>> onSuccess,
                                            OnFailureListener onFailure) {
        String resolvedUid = customerUid;
        if (resolvedUid == null || resolvedUid.trim().isEmpty()) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                resolvedUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                resolvedUid = "demo_cust_uid";
            }
        }
        final String finalCustomerUid = resolvedUid;

        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                .whereEqualTo("customerUid", finalCustomerUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PickupAuthorization> list = new ArrayList<>();
                    if (queryDocumentSnapshots != null) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            PickupAuthorization auth = doc.toObject(PickupAuthorization.class);
                            if (auth != null) {
                                auth.setAuthorizationId(doc.getId());
                                list.add(auth);
                            }
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching authorizations for customer: " + finalCustomerUid, e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    /**
     * Updates authorization status (e.g., VERIFIED or REJECTED) by shop staff or administrator.
     */
    public void updateAuthorizationStatus(String authorizationId,
                                          String newStatus,
                                          String adminUid,
                                          String notes,
                                          OnCompleteListener<Void> onComplete) {
        if (authorizationId == null || authorizationId.trim().isEmpty()) {
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        if (adminUid != null) updates.put("verifiedByAdminUid", adminUid);
        if (notes != null) updates.put("adminNotes", notes);

        db.collection(Constants.COLLECTION_PICKUP_AUTHORIZATIONS)
                .document(authorizationId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }
}
