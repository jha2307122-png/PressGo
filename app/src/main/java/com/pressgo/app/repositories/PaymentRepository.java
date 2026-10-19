package com.pressgo.app.repositories;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pressgo.app.models.Payment;
import com.pressgo.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PaymentRepository {
    private static final String TAG = "PaymentRepository";
    private static PaymentRepository instance;
    private final FirebaseFirestore db;

    private PaymentRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized PaymentRepository getInstance() {
        if (instance == null) {
            instance = new PaymentRepository();
        }
        return instance;
    }

    public void createPayment(Payment payment, OnCompleteListener<Void> onComplete) {
        if (payment.getId() == null || payment.getId().isEmpty()) {
            payment.setId(UUID.randomUUID().toString());
        }
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(Timestamp.now());
        }
        payment.setUpdatedAt(Timestamp.now());

        db.collection(Constants.COLLECTION_PAYMENTS).document(payment.getId())
                .set(payment)
                .addOnCompleteListener(onComplete);
    }

    public void markOrderPaid(String orderId, String method, String transactionRef, OnCompleteListener<Void> onComplete) {
        Map<String, Object> orderUpdates = new HashMap<>();
        orderUpdates.put("paymentStatus", "PAID");
        orderUpdates.put("paidAt", Timestamp.now());
        String token = UUID.randomUUID().toString();
        orderUpdates.put("pickupToken", token);
        if (transactionRef != null && !transactionRef.trim().isEmpty()) {
            orderUpdates.put("transactionRef", transactionRef.trim());
        }
        if (method != null && !method.trim().isEmpty()) {
            orderUpdates.put("paymentMethod", method.trim());
        }

        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .update(orderUpdates)
                .addOnCompleteListener(orderTask -> {
                    getOrderPayment(orderId, payment -> {
                        updatePaymentStatus(payment.getId(), Payment.STATUS_PAID, transactionRef, onComplete);
                    }, err -> {
                        Payment p = new Payment();
                        p.setOrderId(orderId);
                        p.setMethod(method != null ? method : Payment.METHOD_BKASH);
                        p.setStatus(Payment.STATUS_PAID);
                        p.setTransactionRef(transactionRef);
                        createPayment(p, onComplete);
                    });
                });
    }

    public void updatePaymentStatus(String paymentId, String status, String transactionRef,
                                    OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        if (transactionRef != null) {
            updates.put("transactionRef", transactionRef);
        }
        updates.put("updatedAt", Timestamp.now());

        db.collection(Constants.COLLECTION_PAYMENTS).document(paymentId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }

    public void getOrderPayment(String orderId,
                                OnSuccessListener<Payment> onSuccess,
                                OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_PAYMENTS)
                .whereEqualTo("orderId", orderId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Payment p = doc.toObject(Payment.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            if (onSuccess != null) onSuccess.onSuccess(p);
                        }
                    } else {
                        if (onFailure != null) onFailure.onFailure(new Exception("Payment not found"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void getCustomerPayments(String customerId,
                                    OnSuccessListener<List<Payment>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_PAYMENTS)
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Payment> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Payment p = doc.toObject(Payment.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            list.add(p);
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }

    public void processRefund(String paymentId, double amount, String reason,
                              OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Payment.STATUS_REFUNDED);
        updates.put("refundAmount", amount);
        updates.put("refundReason", reason);
        updates.put("updatedAt", Timestamp.now());

        db.collection(Constants.COLLECTION_PAYMENTS).document(paymentId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }
}
