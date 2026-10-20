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
import com.google.firebase.firestore.QuerySnapshot;
import com.pressgo.app.models.InventoryItem;
import com.pressgo.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryRepository {
    private static final String TAG = "InventoryRepository";
    private static InventoryRepository instance;
    private final FirebaseFirestore db;

    private InventoryRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized InventoryRepository getInstance() {
        if (instance == null) {
            instance = new InventoryRepository();
        }
        return instance;
    }

    public void getShopInventory(String shopId,
                                 OnSuccessListener<List<InventoryItem>> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_INVENTORY)
                .whereEqualTo("shopId", shopId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<InventoryItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        InventoryItem item = doc.toObject(InventoryItem.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            list.add(item);
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching inventory", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    public void addInventoryItem(InventoryItem item, OnCompleteListener<Void> onComplete) {
        if (item.getId() == null || item.getId().isEmpty()) {
            item.setId(UUID.randomUUID().toString());
        }
        item.setLastUpdated(Timestamp.now());
        item.setLowStock(item.getQuantity() <= item.getLowStockThreshold());
        db.collection(Constants.COLLECTION_INVENTORY).document(item.getId())
                .set(item)
                .addOnCompleteListener(onComplete);
    }

    public void updateInventoryItem(InventoryItem item, OnCompleteListener<Void> onComplete) {
        item.setLastUpdated(Timestamp.now());
        item.setLowStock(item.getQuantity() <= item.getLowStockThreshold());
        db.collection(Constants.COLLECTION_INVENTORY).document(item.getId())
                .set(item)
                .addOnCompleteListener(onComplete);
    }

    public void deleteInventoryItem(String itemId, OnCompleteListener<Void> onComplete) {
        db.collection(Constants.COLLECTION_INVENTORY).document(itemId)
                .delete()
                .addOnCompleteListener(onComplete);
    }

    public void getLowStockItems(String shopId,
                                 OnSuccessListener<List<InventoryItem>> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_INVENTORY)
                .whereEqualTo("shopId", shopId)
                .whereEqualTo("lowStock", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<InventoryItem> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        InventoryItem item = doc.toObject(InventoryItem.class);
                        if (item != null) {
                            item.setId(doc.getId());
                            list.add(item);
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }

    public ListenerRegistration addInventoryListener(String shopId, EventListener<QuerySnapshot> listener) {
        return db.collection(Constants.COLLECTION_INVENTORY)
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener(listener);
    }
}
