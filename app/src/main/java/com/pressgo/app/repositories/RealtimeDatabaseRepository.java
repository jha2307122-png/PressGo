package com.pressgo.app.repositories;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.pressgo.app.models.LiveNotification;
import com.pressgo.app.models.LiveOrderStatus;
import com.pressgo.app.models.LivePrinterStatus;
import com.pressgo.app.models.LiveShopStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized repository layer for Firebase Realtime Database.
 * Manages live, low-latency synchronization for:
 *   - /liveOrders/{orderId}
 *   - /printerStatus/{printerId}
 *   - /shopStatus/{shopId}
 *   - /liveNotifications/{userId}/{notificationId}
 *
 * Designed for complete safety, null-safe deserialization, lifecycle-safe unregistration,
 * and graceful fallback if Realtime Database is not yet provisioned in the Firebase Console.
 */
public class RealtimeDatabaseRepository {

    private static final String TAG = "RealtimeDbRepo";

    // Root paths in Realtime Database
    public static final String PATH_LIVE_ORDERS       = "liveOrders";
    public static final String PATH_PRINTER_STATUS    = "printerStatus";
    public static final String PATH_SHOP_STATUS       = "shopStatus";
    public static final String PATH_LIVE_NOTIFS       = "liveNotifications";

    private static RealtimeDatabaseRepository instance;

    private FirebaseDatabase database;
    private boolean persistenceAttempted = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Interfaces
    // ─────────────────────────────────────────────────────────────────────────

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public interface LiveListener<T> {
        void onUpdate(T data);
        void onError(Exception error);
    }

    /**
     * Handle returned to callers when attaching listeners.
     * Call remove() during Activity/Fragment onDestroy() to prevent memory leaks.
     */
    public interface ListenerHandle {
        void remove();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────────────────

    private RealtimeDatabaseRepository() {
        initDatabase();
    }

    public static synchronized RealtimeDatabaseRepository getInstance() {
        if (instance == null) {
            instance = new RealtimeDatabaseRepository();
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database initialization & availability
    // ─────────────────────────────────────────────────────────────────────────

    private synchronized FirebaseDatabase initDatabase() {
        if (database != null) {
            return database;
        }

        try {
            database = FirebaseDatabase.getInstance();
            if (!persistenceAttempted) {
                persistenceAttempted = true;
                try {
                    database.setPersistenceEnabled(true);
                } catch (Exception ignored) {
                    // setPersistenceEnabled can only be invoked before any database operations
                }
            }
            Log.d(TAG, "Firebase Realtime Database initialized successfully.");
        } catch (Exception e) {
            Log.w(TAG, "Firebase Realtime Database instance unavailable: " + e.getMessage() +
                    ". If Realtime Database is not enabled in Firebase Console, please create it in your console.");
            database = null;
        }

        return database;
    }

    /**
     * Allows setting an explicit database URL if not configured in google-services.json.
     * Note: Never invent a URL; only use the official URL provided in the Firebase Console.
     */
    public synchronized void setDatabaseUrl(@NonNull String url) {
        try {
            database = FirebaseDatabase.getInstance(url.trim());
            Log.i(TAG, "Configured Realtime Database with explicit URL: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Realtime Database with URL: " + url, e);
        }
    }

    /**
     * Checks if the Realtime Database instance is initialized and accessible.
     */
    public boolean isRealtimeDatabaseAvailable() {
        return initDatabase() != null;
    }

    @Nullable
    private DatabaseReference getRootReference() {
        FirebaseDatabase db = initDatabase();
        if (db == null) return null;
        try {
            return db.getReference();
        } catch (Exception e) {
            Log.w(TAG, "getRootReference failed: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Live Order Tracking (/liveOrders/{orderId})
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Atomically publishes or updates a live order status in /liveOrders/{orderId}.
     */
    public void updateLiveOrderStatus(@NonNull String orderId,
                                      @NonNull String status,
                                      @Nullable String customerId,
                                      @Nullable String shopId,
                                      @Nullable String staffNotes,
                                      @Nullable String pickupToken,
                                      @Nullable Integer estWaitMinutes,
                                      @Nullable Callback<Void> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("orderId", orderId);
        updates.put("orderStatus", status);
        updates.put("status", status);
        if (customerId != null) updates.put("customerId", customerId);
        if (shopId != null) updates.put("shopId", shopId);
        if (staffNotes != null) updates.put("staffNotes", staffNotes);
        if (pickupToken != null) updates.put("pickupToken", pickupToken);
        if (estWaitMinutes != null) updates.put("estimatedWaitMinutes", estWaitMinutes);
        updates.put("updatedAt", ServerValue.TIMESTAMP);

        root.child(PATH_LIVE_ORDERS).child(orderId).updateChildren(updates, (error, ref) -> {
            if (error != null) {
                Log.e(TAG, "Error updating live order status: " + error.getMessage());
                if (callback != null) callback.onError(error.toException());
            } else {
                Log.d(TAG, "Live order " + orderId + " updated to status: " + status);
                if (callback != null) callback.onSuccess(null);
            }
        });
    }

    /**
     * Updates live order status using LiveOrderStatus model.
     */
    public void updateLiveOrderStatus(@NonNull LiveOrderStatus liveStatus, @Nullable Callback<Void> callback) {
        if (liveStatus.getOrderId() == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("OrderId cannot be null"));
            return;
        }
        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        root.child(PATH_LIVE_ORDERS).child(liveStatus.getOrderId()).updateChildren(liveStatus.toMap(), (error, ref) -> {
            if (error != null) {
                if (callback != null) callback.onError(error.toException());
            } else {
                if (callback != null) callback.onSuccess(null);
            }
        });
    }

    /**
     * Listens to real-time updates for a single order at /liveOrders/{orderId}.
     * Returns a ListenerHandle to remove listener on onDestroy().
     */
    @NonNull
    public ListenerHandle listenToOrderStatus(@NonNull String orderId, @NonNull LiveListener<LiveOrderStatus> listener) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            listener.onError(new IllegalStateException("Realtime Database not available"));
            return () -> {};
        }

        DatabaseReference orderRef = root.child(PATH_LIVE_ORDERS).child(orderId);
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        LiveOrderStatus status = snapshot.getValue(LiveOrderStatus.class);
                        if (status != null) {
                            if (status.getOrderId() == null) {
                                status.setOrderId(snapshot.getKey());
                            }
                            listener.onUpdate(status);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing LiveOrderStatus: " + e.getMessage());
                        listener.onError(e);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "listenToOrderStatus cancelled: " + error.getMessage());
                listener.onError(error.toException());
            }
        };

        orderRef.addValueEventListener(valueListener);
        return () -> orderRef.removeEventListener(valueListener);
    }

    /**
     * One-time fetch of live order status.
     */
    public void getLiveOrderStatus(@NonNull String orderId, @NonNull Callback<LiveOrderStatus> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        root.child(PATH_LIVE_ORDERS).child(orderId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    LiveOrderStatus status = snapshot.getValue(LiveOrderStatus.class);
                    if (status != null && status.getOrderId() == null) {
                        status.setOrderId(snapshot.getKey());
                    }
                    callback.onSuccess(status);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Printer Fleet Telemetry (/printerStatus/{printerId})
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates printer availability and telemetry in /printerStatus/{printerId}.
     */
    public void updatePrinterStatus(@NonNull String printerId,
                                    @Nullable String shopId,
                                    @Nullable String name,
                                    @NonNull String status,
                                    int paperLevel,
                                    int inkLevel,
                                    int tonerLevel,
                                    @Nullable String currentJobId,
                                    @Nullable Callback<Void> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("printerId", printerId);
        if (shopId != null) map.put("shopId", shopId);
        if (name != null) map.put("name", name);
        if (status != null) map.put("status", status);
        if (paperLevel >= 0) map.put("paperLevel", paperLevel);
        if (inkLevel >= 0) map.put("inkLevel", inkLevel);
        if (tonerLevel >= 0) map.put("tonerLevel", tonerLevel);
        if (currentJobId != null) map.put("currentJobId", currentJobId);
        map.put("updatedAt", ServerValue.TIMESTAMP);

        root.child(PATH_PRINTER_STATUS).child(printerId).updateChildren(map, (error, ref) -> {
            if (error != null) {
                Log.e(TAG, "Error updating printer status: " + error.getMessage());
                if (callback != null) callback.onError(error.toException());
            } else {
                Log.d(TAG, "Printer " + printerId + " live status updated to: " + status);
                if (callback != null) callback.onSuccess(null);
            }
        });
    }

    /**
     * Listens to live status of a single printer at /printerStatus/{printerId}.
     */
    @NonNull
    public ListenerHandle listenToPrinterStatus(@NonNull String printerId,
                                                @NonNull LiveListener<LivePrinterStatus> listener) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            listener.onError(new IllegalStateException("Realtime Database not available"));
            return () -> {};
        }

        DatabaseReference printerRef = root.child(PATH_PRINTER_STATUS).child(printerId);
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        LivePrinterStatus status = snapshot.getValue(LivePrinterStatus.class);
                        if (status != null) {
                            if (status.getPrinterId() == null) {
                                status.setPrinterId(snapshot.getKey());
                            }
                            listener.onUpdate(status);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing LivePrinterStatus: " + e.getMessage());
                        listener.onError(e);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "listenToPrinterStatus cancelled: " + error.getMessage());
                listener.onError(error.toException());
            }
        };

        printerRef.addValueEventListener(valueListener);
        return () -> printerRef.removeEventListener(valueListener);
    }

    /**
     * Listens to live status for all printers associated with a specific shop.
     */
    @NonNull
    public ListenerHandle listenToShopPrinters(@NonNull String shopId,
                                               @NonNull LiveListener<List<LivePrinterStatus>> listener) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            listener.onError(new IllegalStateException("Realtime Database not available"));
            return () -> {};
        }

        Query query = root.child(PATH_PRINTER_STATUS).orderByChild("shopId").equalTo(shopId);
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<LivePrinterStatus> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        LivePrinterStatus status = child.getValue(LivePrinterStatus.class);
                        if (status != null) {
                            if (status.getPrinterId() == null) {
                                status.setPrinterId(child.getKey());
                            }
                            list.add(status);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing printer entry: " + e.getMessage());
                    }
                }
                listener.onUpdate(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "listenToShopPrinters cancelled: " + error.getMessage());
                listener.onError(error.toException());
            }
        };

        query.addValueEventListener(valueListener);
        return () -> query.removeEventListener(valueListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Shop Availability & Queue Telemetry (/shopStatus/{shopId})
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates shop operational availability (Open/Closed) and queue estimate.
     */
    public void updateShopStatus(@NonNull String shopId,
                                 boolean isOpen,
                                 int queueLength,
                                 int estWaitMinutes,
                                 @Nullable Callback<Void> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("isOpen", isOpen);
        map.put("open", isOpen);
        if (queueLength >= 0) map.put("queueLength", queueLength);
        if (estWaitMinutes >= 0) map.put("estimatedWaitMinutes", estWaitMinutes);
        map.put("updatedAt", ServerValue.TIMESTAMP);

        root.child(PATH_SHOP_STATUS).child(shopId).updateChildren(map, (error, ref) -> {
            if (error != null) {
                Log.e(TAG, "Error updating shop status: " + error.getMessage());
                if (callback != null) callback.onError(error.toException());
            } else {
                Log.d(TAG, "Shop " + shopId + " live status updated: isOpen=" + isOpen);
                if (callback != null) callback.onSuccess(null);
            }
        });
    }

    /**
     * Listens to live open/close and queue status of a shop.
     */
    @NonNull
    public ListenerHandle listenToShopStatus(@NonNull String shopId,
                                             @NonNull LiveListener<LiveShopStatus> listener) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            listener.onError(new IllegalStateException("Realtime Database not available"));
            return () -> {};
        }

        DatabaseReference shopRef = root.child(PATH_SHOP_STATUS).child(shopId);
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        LiveShopStatus status = snapshot.getValue(LiveShopStatus.class);
                        if (status == null) {
                            status = new LiveShopStatus();
                        }
                        if (status.getShopId() == null) {
                            status.setShopId(snapshot.getKey());
                        }
                        Boolean bOpen = snapshot.child("isOpen").getValue(Boolean.class);
                        if (bOpen == null) {
                            bOpen = snapshot.child("open").getValue(Boolean.class);
                        }
                        if (bOpen != null) {
                            status.setIsOpen(bOpen);
                            status.setOpen(bOpen);
                        }
                        listener.onUpdate(status);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing LiveShopStatus: " + e.getMessage());
                        listener.onError(e);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "listenToShopStatus cancelled: " + error.getMessage());
                listener.onError(error.toException());
            }
        };

        shopRef.addValueEventListener(valueListener);
        return () -> shopRef.removeEventListener(valueListener);
    }

    /**
     * One-time fetch of live shop status.
     */
    public void getLiveShopStatus(@NonNull String shopId, @NonNull Callback<LiveShopStatus> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        root.child(PATH_SHOP_STATUS).child(shopId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    LiveShopStatus status = snapshot.getValue(LiveShopStatus.class);
                    if (status == null) {
                        status = new LiveShopStatus();
                    }
                    if (status.getShopId() == null) {
                        status.setShopId(snapshot.getKey());
                    }
                    Boolean bOpen = snapshot.child("isOpen").getValue(Boolean.class);
                    if (bOpen == null) {
                        bOpen = snapshot.child("open").getValue(Boolean.class);
                    }
                    if (bOpen != null) {
                        status.setIsOpen(bOpen);
                        status.setOpen(bOpen);
                    }
                    callback.onSuccess(status);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.toException());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Live Notifications (/liveNotifications/{userId}/{notificationId})
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a real-time notification to a specific user.
     */
    public void sendLiveNotification(@NonNull LiveNotification notification,
                                     @Nullable Callback<Void> callback) {
        if (notification.getUserId() == null || notification.getId() == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("UserId and NotificationId are required"));
            return;
        }

        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        root.child(PATH_LIVE_NOTIFS)
                .child(notification.getUserId())
                .child(notification.getId())
                .setValue(notification.toMap(), (error, ref) -> {
                    if (error != null) {
                        Log.e(TAG, "Error sending live notification: " + error.getMessage());
                        if (callback != null) callback.onError(error.toException());
                    } else {
                        Log.d(TAG, "Live notification sent to user " + notification.getUserId());
                        if (callback != null) callback.onSuccess(null);
                    }
                });
    }

    /**
     * Listens for incoming real-time notifications for a specific user.
     */
    @NonNull
    public ListenerHandle listenToLiveNotifications(@NonNull String userId,
                                                    @NonNull LiveListener<List<LiveNotification>> listener) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            listener.onError(new IllegalStateException("Realtime Database not available"));
            return () -> {};
        }

        DatabaseReference notifRef = root.child(PATH_LIVE_NOTIFS).child(userId);
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<LiveNotification> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        LiveNotification n = child.getValue(LiveNotification.class);
                        if (n != null) {
                            if (n.getId() == null) {
                                n.setId(child.getKey());
                            }
                            list.add(n);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing LiveNotification: " + e.getMessage());
                    }
                }
                // Sort newest first
                Collections.sort(list, (a, b) -> {
                    Long t1 = a.getTimestamp();
                    Long t2 = b.getTimestamp();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                });
                listener.onUpdate(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "listenToLiveNotifications cancelled: " + error.getMessage());
                listener.onError(error.toException());
            }
        };

        notifRef.addValueEventListener(valueListener);
        return () -> notifRef.removeEventListener(valueListener);
    }

    /**
     * Marks a live notification as read in /liveNotifications/{userId}/{notificationId}.
     */
    public void markLiveNotificationRead(@NonNull String userId,
                                         @NonNull String notificationId,
                                         @Nullable Callback<Void> callback) {
        DatabaseReference root = getRootReference();
        if (root == null) {
            if (callback != null) callback.onError(new IllegalStateException("Realtime Database not available"));
            return;
        }

        root.child(PATH_LIVE_NOTIFS)
                .child(userId)
                .child(notificationId)
                .child("read")
                .setValue(true, (error, ref) -> {
                    if (error != null) {
                        if (callback != null) callback.onError(error.toException());
                    } else {
                        if (callback != null) callback.onSuccess(null);
                    }
                });
    }
}
