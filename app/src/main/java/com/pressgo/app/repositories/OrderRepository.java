package com.pressgo.app.repositories;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.pressgo.app.models.Notification;
import com.pressgo.app.models.OrderFile;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class OrderRepository {
    private static final String TAG = "OrderRepository";
    private static OrderRepository instance;
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;

    private OrderRepository() {
        db = FirebaseFirestore.getInstance();
        storage = com.pressgo.app.utils.FileDownloadUtils.getFirebaseStorage();
    }

    public static synchronized OrderRepository getInstance() {
        if (instance == null) {
            instance = new OrderRepository();
        }
        return instance;
    }

    public String generateOrderId() {
        String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        int randomNum = 1000 + new Random().nextInt(9000);
        return "PG-" + dateStr + "-" + randomNum;
    }

    public void createOrder(PrintOrder order, List<OrderFile> files,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        if (order.getId() == null || order.getId().isEmpty()) {
            order.setId(generateOrderId());
        }

        DocumentReference orderRef = db.collection(Constants.COLLECTION_ORDERS).document(order.getId());
        orderRef.set(order)
                .addOnSuccessListener(aVoid -> {
                    if (files != null && !files.isEmpty()) {
                        for (OrderFile file : files) {
                            file.setOrderId(order.getId());
                            orderRef.collection(Constants.COLLECTION_ORDER_FILES)
                                    .document(file.getId() != null ? file.getId() : UUID.randomUUID().toString())
                                    .set(file);
                        }
                    }
                    if (onSuccess != null) {
                        onSuccess.onSuccess(order.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating order", e);
                    if (onFailure != null) {
                        onFailure.onFailure(e);
                    }
                });
    }

    public void getCustomerOrders(String customerId,
                                  OnSuccessListener<List<PrintOrder>> onSuccess,
                                  OnFailureListener onFailure) {
        getCustomerOrders(customerId, null, onSuccess, onFailure);
    }

    public void getCustomerOrders(String customerId,
                                  String customerEmail,
                                  OnSuccessListener<List<PrintOrder>> onSuccess,
                                  OnFailureListener onFailure) {
        if (customerId == null || customerId.trim().isEmpty()) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                customerId = "guest_user";
            }
        }

        final String finalCustomerId = customerId;
        final boolean isDemoOrGuest = "demo_cust_uid".equals(customerId) || "guest_user".equals(customerId);

        com.google.android.gms.tasks.Task<QuerySnapshot> primaryTask;
        if (isDemoOrGuest) {
            primaryTask = db.collection(Constants.COLLECTION_ORDERS)
                    .whereIn("customerId", Arrays.asList("demo_cust_uid", "guest_user"))
                    .get();
        } else {
            primaryTask = db.collection(Constants.COLLECTION_ORDERS)
                    .whereEqualTo("customerId", finalCustomerId)
                    .get();
        }

        primaryTask.addOnSuccessListener(queryDocumentSnapshots -> {
            Map<String, PrintOrder> orderMap = new LinkedHashMap<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                PrintOrder order = doc.toObject(PrintOrder.class);
                if (order != null) {
                    order.setId(doc.getId());
                    if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
                        order.setOrderId(doc.getId());
                    }
                    orderMap.put(doc.getId(), order);
                }
            }

            // If customer has an email, also fetch orders associated with that email and merge
            if (customerEmail != null && !customerEmail.trim().isEmpty()) {
                db.collection(Constants.COLLECTION_ORDERS)
                        .whereEqualTo("customerEmail", customerEmail.trim())
                        .get()
                        .addOnSuccessListener(emailSnapshots -> {
                            for (DocumentSnapshot doc : emailSnapshots) {
                                PrintOrder order = doc.toObject(PrintOrder.class);
                                if (order != null) {
                                    order.setId(doc.getId());
                                    if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
                                        order.setOrderId(doc.getId());
                                    }
                                    orderMap.put(doc.getId(), order);
                                }
                            }
                            deliverSortedOrders(orderMap, onSuccess);
                        })
                        .addOnFailureListener(e -> {
                            // If email query fails, deliver primary results
                            deliverSortedOrders(orderMap, onSuccess);
                        });
            } else {
                deliverSortedOrders(orderMap, onSuccess);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching customer orders", e);
            if (onFailure != null) onFailure.onFailure(e);
        });
    }

    private void deliverSortedOrders(Map<String, PrintOrder> orderMap, OnSuccessListener<List<PrintOrder>> onSuccess) {
        List<PrintOrder> orders = new ArrayList<>(orderMap.values());
        Collections.sort(orders, (a, b) -> {
            Timestamp t1 = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
            Timestamp t2 = b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });
        if (onSuccess != null) onSuccess.onSuccess(orders);
    }

    public ListenerRegistration addCustomerOrdersListener(String customerId,
                                                          EventListener<QuerySnapshot> listener) {
        if (customerId == null || customerId.trim().isEmpty()) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            } else {
                customerId = "guest_user";
            }
        }
        if ("demo_cust_uid".equals(customerId) || "guest_user".equals(customerId)) {
            return db.collection(Constants.COLLECTION_ORDERS)
                    .whereIn("customerId", Arrays.asList("demo_cust_uid", "guest_user"))
                    .addSnapshotListener(listener);
        }
        return db.collection(Constants.COLLECTION_ORDERS)
                .whereEqualTo("customerId", customerId)
                .addSnapshotListener(listener);
    }

    public void getShopOrders(String shopId,
                              OnSuccessListener<List<PrintOrder>> onSuccess,
                              OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_ORDERS)
                .whereEqualTo("shopId", shopId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PrintOrder> orders = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        PrintOrder order = doc.toObject(PrintOrder.class);
                        if (order != null) {
                            order.setId(doc.getId());
                            if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
                                order.setOrderId(doc.getId());
                            }
                            orders.add(order);
                        }
                    }
                    Collections.sort(orders, (a, b) -> {
                        Timestamp t1 = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
                        Timestamp t2 = b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });
                    if (onSuccess != null) onSuccess.onSuccess(orders);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching shop orders", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    public void getOrderById(String orderId,
                             OnSuccessListener<PrintOrder> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        PrintOrder order = doc.toObject(PrintOrder.class);
                        if (order != null) {
                            order.setId(doc.getId());
                            if (onSuccess != null) onSuccess.onSuccess(order);
                        }
                    } else {
                        if (onFailure != null) onFailure.onFailure(new Exception("Order not found"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void updateOrderStatus(String orderId, String newStatus, String notes,
                                  OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("orderStatus", newStatus);
        updates.put("status", newStatus);
        if (notes != null && !notes.isEmpty()) {
            updates.put("staffNotes", notes);
        }

        Timestamp now = Timestamp.now();
        if (PrintOrder.STATUS_ACCEPTED.equals(newStatus)) {
            updates.put("acceptedAt", now);
        } else if (PrintOrder.STATUS_PRINTING.equals(newStatus)) {
            updates.put("printingStartedAt", now);
        } else if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(newStatus)) {
            updates.put("readyAt", now);
            updates.put("pickupToken", UUID.randomUUID().toString());
        } else if (PrintOrder.STATUS_COMPLETED.equals(newStatus)) {
            updates.put("completedAt", now);
            updates.put("paymentStatus", PrintOrder.PAYMENT_PAID);
        }

        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        createStatusNotification(orderId, newStatus);
                    }
                    if (onComplete != null) {
                        onComplete.onComplete(task);
                    }
                });
    }

    private void createStatusNotification(String orderId, String newStatus) {
        db.collection(Constants.COLLECTION_ORDERS).document(orderId).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                String customerId = doc.getString("customerId");
                if (customerId == null || customerId.isEmpty()) return;

                String title = null;
                String message = null;
                String type = Notification.TYPE_GENERAL;

                if (PrintOrder.STATUS_ACCEPTED.equals(newStatus)) {
                    title = "Order Accepted";
                    message = "Your print order has been accepted and added to the printing queue.";
                    type = Notification.TYPE_ORDER_ACCEPTED;
                } else if (PrintOrder.STATUS_PRINTING.equals(newStatus)) {
                    title = "Printing in Progress";
                    message = "Your documents are currently being printed.";
                    type = Notification.TYPE_PRINTING_STARTED;
                } else if (PrintOrder.STATUS_READY_FOR_PICKUP.equals(newStatus)) {
                    title = "Printout Ready! 📄";
                    message = "Your order is printed and ready! Pay online from the app to receive your pickup QR code.";
                    type = Notification.TYPE_READY_FOR_PICKUP;
                } else if (PrintOrder.STATUS_COMPLETED.equals(newStatus)) {
                    title = "Order Completed! ✅";
                    message = "Your print order has been handed over. Thank you for using PressGo!";
                    type = Notification.TYPE_ORDER_COMPLETED;
                } else if (PrintOrder.STATUS_REJECTED.equals(newStatus)) {
                    title = "Order Rejected";
                    message = "Unfortunately, your print order could not be accepted.";
                    type = Notification.TYPE_ORDER_REJECTED;
                } else if (PrintOrder.STATUS_CANCELLED.equals(newStatus)) {
                    title = "Order Cancelled";
                    message = "Your print order has been cancelled.";
                    type = Notification.TYPE_ORDER_CANCELLED;
                }

                if (title != null) {
                    Notification notif = new Notification(
                            UUID.randomUUID().toString(),
                            customerId,
                            orderId,
                            title,
                            message,
                            type,
                            false,
                            Timestamp.now(),
                            null
                    );
                    NotificationRepository.getInstance().createNotification(notif, null);
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error generating notification for status " + newStatus, e));
    }

    public void updateOrderStatus(String orderId, String newStatus, String notes,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        updateOrderStatus(orderId, newStatus, notes, task -> {
            if (task.isSuccessful()) {
                if (onSuccess != null) onSuccess.onSuccess(task.getResult());
            } else {
                if (onFailure != null) onFailure.onFailure(task.getException());
            }
        });
    }

    public ListenerRegistration addOrderStatusListener(String orderId, EventListener<DocumentSnapshot> listener) {
        return db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .addSnapshotListener(listener);
    }

    public void cancelOrder(String orderId, String reason, OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("orderStatus", PrintOrder.STATUS_CANCELLED);
        updates.put("status", PrintOrder.STATUS_CANCELLED);
        updates.put("cancellationReason", reason);
        updates.put("refundStatus", "REQUESTED");
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        createStatusNotification(orderId, PrintOrder.STATUS_CANCELLED);
                    }
                    if (onComplete != null) {
                        onComplete.onComplete(task);
                    }
                });
    }

    public void generatePickupToken(String orderId,
                                    OnSuccessListener<String> onSuccess,
                                    OnFailureListener onFailure) {
        String token = UUID.randomUUID().toString();
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .update("pickupToken", token)
                .addOnSuccessListener(aVoid -> {
                    if (onSuccess != null) onSuccess.onSuccess(token);
                })
                .addOnFailureListener(onFailure);
    }

    public void verifyPickupToken(String orderId, String token,
                                  OnSuccessListener<Boolean> onSuccess,
                                  OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String storedToken = doc.getString("pickupToken");
                        boolean matches = token != null && token.equals(storedToken);
                        if (matches) {
                            updateOrderStatus(orderId, PrintOrder.STATUS_COMPLETED, "Picked up via QR verification", task -> {});
                        }
                        if (onSuccess != null) onSuccess.onSuccess(matches);
                    } else {
                        if (onFailure != null) onFailure.onFailure(new Exception("Order not found"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void uploadOrderFile(String orderId, OrderFile file, InputStream stream,
                                OnProgressListener<UploadTask.TaskSnapshot> onProgress,
                                OnSuccessListener<String> onSuccess,
                                OnFailureListener onFailure) {
        String storagePath = "orders/" + orderId + "/files/" + UUID.randomUUID().toString() + "_" + file.getFileName();
        StorageReference fileRef = storage.getReference().child(storagePath);

        UploadTask uploadTask = fileRef.putStream(stream);
        if (onProgress != null) {
            uploadTask.addOnProgressListener(onProgress);
        }

        uploadTask.addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    file.setStoragePath(storagePath);
                    file.setDownloadUrl(uri.toString());
                    file.setUploaded(true);
                    file.setUploadedAt(Timestamp.now());

                    db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                            .collection(Constants.COLLECTION_ORDER_FILES)
                            .document(file.getId() != null ? file.getId() : UUID.randomUUID().toString())
                            .set(file)
                            .addOnSuccessListener(aVoid -> {
                                if (onSuccess != null) onSuccess.onSuccess(uri.toString());
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure))
                .addOnFailureListener(onFailure);
    }

    public void getOrderFiles(String orderId,
                              OnSuccessListener<List<OrderFile>> onSuccess,
                              OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_ORDERS).document(orderId)
                .collection(Constants.COLLECTION_ORDER_FILES)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<OrderFile> files = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        OrderFile file = doc.toObject(OrderFile.class);
                        if (file != null) {
                            file.setId(doc.getId());
                            files.add(file);
                        }
                    }
                    if (!files.isEmpty()) {
                        if (onSuccess != null) onSuccess.onSuccess(files);
                    } else {
                        // Fallback: Check Firebase Storage directly at orders/<orderId>/files
                        storage.getReference().child("orders/" + orderId + "/files").listAll()
                                .addOnSuccessListener(listResult -> {
                                    if (listResult != null && !listResult.getItems().isEmpty()) {
                                        for (StorageReference item : listResult.getItems()) {
                                            OrderFile file = new OrderFile();
                                            file.setId(item.getName());
                                            file.setOrderId(orderId);
                                            String name = item.getName();
                                            int underIdx = name.indexOf('_');
                                            file.setFileName(underIdx >= 0 && underIdx < name.length() - 1 ? name.substring(underIdx + 1) : name);
                                            file.setStoragePath(item.getPath());
                                            file.setUploaded(true);
                                            files.add(file);
                                        }
                                    }
                                    if (onSuccess != null) onSuccess.onSuccess(files);
                                })
                                .addOnFailureListener(e -> {
                                    if (onSuccess != null) onSuccess.onSuccess(files);
                                });
                    }
                })
                .addOnFailureListener(onFailure);
    }
}
