package com.pressgo.app.repositories;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pressgo.app.models.Notification;
import com.pressgo.app.utils.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationRepository {
    private static final String TAG = "NotificationRepository";
    private static NotificationRepository instance;
    private final FirebaseFirestore db;

    private NotificationRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    public void createNotification(Notification notification, OnCompleteListener<Void> onComplete) {
        if (notification.getId() == null || notification.getId().isEmpty()) {
            notification.setId(UUID.randomUUID().toString());
        }
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(Timestamp.now());
        }

        db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .document(notification.getId())
                .set(notification)
                .addOnCompleteListener(onComplete);
    }

    public void getUserNotifications(String userId,
                                     OnSuccessListener<List<Notification>> onSuccess,
                                     OnFailureListener onFailure) {
        Query query;
        if (userId == null || userId.trim().isEmpty() || "demo_cust_uid".equals(userId) || "guest_user".equals(userId)) {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereIn("userId", Arrays.asList("demo_cust_uid", "guest_user", ""));
        } else {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId);
        }

        // Query WITHOUT orderBy("createdAt") to avoid Firestore FAILED_PRECONDITION composite index error!
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Notification notif = doc.toObject(Notification.class);
                        if (notif != null) {
                            notif.setId(doc.getId());
                            list.add(notif);
                        }
                    }
                    // Sort in memory by createdAt descending (newest first)
                    Collections.sort(list, (n1, n2) -> {
                        Timestamp t1 = n1.getCreatedAt();
                        Timestamp t2 = n2.getCreatedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });

                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting notifications", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    public void markAsRead(String notificationId, OnCompleteListener<Void> onComplete) {
        if (notificationId == null || notificationId.isEmpty()) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }

    public void markAllAsRead(String userId, OnCompleteListener<Void> onComplete) {
        Query query;
        if (userId == null || userId.trim().isEmpty() || "demo_cust_uid".equals(userId) || "guest_user".equals(userId)) {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereIn("userId", Arrays.asList("demo_cust_uid", "guest_user", ""));
        } else {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        Boolean isRead = doc.getBoolean("read");
                        if (isRead == null || !isRead) {
                            doc.getReference().update("read", true);
                        }
                    }
                    if (onComplete != null) onComplete.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error marking all read", e);
                    if (onComplete != null) onComplete.onComplete(null);
                });
    }

    public void getUnreadCount(String userId,
                               OnSuccessListener<Integer> onSuccess,
                               OnFailureListener onFailure) {
        Query query;
        if (userId == null || userId.trim().isEmpty() || "demo_cust_uid".equals(userId) || "guest_user".equals(userId)) {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereIn("userId", Arrays.asList("demo_cust_uid", "guest_user", ""));
        } else {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    int unread = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Boolean isRead = doc.getBoolean("read");
                        if (isRead == null || !isRead) {
                            unread++;
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(unread);
                })
                .addOnFailureListener(onFailure);
    }

    public ListenerRegistration addNotificationsListener(String userId, EventListener<QuerySnapshot> listener) {
        Query query;
        if (userId == null || userId.trim().isEmpty() || "demo_cust_uid".equals(userId) || "guest_user".equals(userId)) {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereIn("userId", Arrays.asList("demo_cust_uid", "guest_user", ""));
        } else {
            query = db.collection(Constants.COLLECTION_NOTIFICATIONS)
                    .whereEqualTo("userId", userId);
        }
        // Do NOT use orderBy here - avoids composite index failure
        return query.addSnapshotListener(listener);
    }
}
