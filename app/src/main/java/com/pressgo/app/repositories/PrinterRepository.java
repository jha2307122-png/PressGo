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
import com.pressgo.app.models.Printer;
import com.pressgo.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PrinterRepository {
    private static final String TAG = "PrinterRepository";
    private static PrinterRepository instance;
    private final FirebaseFirestore db;

    private PrinterRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized PrinterRepository getInstance() {
        if (instance == null) {
            instance = new PrinterRepository();
        }
        return instance;
    }

    public void getShopPrinters(String shopId,
                                OnSuccessListener<List<Printer>> onSuccess,
                                OnFailureListener onFailure) {
        db.collection(Constants.COLLECTION_PRINTERS)
                .whereEqualTo("shopId", shopId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Printer> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Printer p = doc.toObject(Printer.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            list.add(p);
                        }
                    }
                    if (onSuccess != null) onSuccess.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching printers", e);
                    if (onFailure != null) onFailure.onFailure(e);
                });
    }

    public void getActivePrintersForShop(String shopId,
                                         OnSuccessListener<List<Printer>> onSuccess,
                                         OnFailureListener onFailure) {
        getShopPrinters(shopId, onSuccess, onFailure);
    }

    public void updatePrinterStatus(String printerId, String status, OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("lastUpdated", Timestamp.now());
        db.collection(Constants.COLLECTION_PRINTERS).document(printerId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }

    public void updatePrinterLevels(String printerId, int paperLevel, int inkLevel, int tonerLevel,
                                    OnCompleteListener<Void> onComplete) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("paperLevel", paperLevel);
        updates.put("inkLevel", inkLevel);
        updates.put("tonerLevel", tonerLevel);
        updates.put("lastUpdated", Timestamp.now());
        db.collection(Constants.COLLECTION_PRINTERS).document(printerId)
                .update(updates)
                .addOnCompleteListener(onComplete);
    }

    public void addPrinter(Printer printer, OnCompleteListener<Void> onComplete) {
        if (printer.getId() == null || printer.getId().isEmpty()) {
            printer.setId(UUID.randomUUID().toString());
        }
        printer.setLastUpdated(Timestamp.now());
        db.collection(Constants.COLLECTION_PRINTERS).document(printer.getId())
                .set(printer)
                .addOnCompleteListener(onComplete);
    }

    public ListenerRegistration addPrintersListener(String shopId, EventListener<QuerySnapshot> listener) {
        return db.collection(Constants.COLLECTION_PRINTERS)
                .whereEqualTo("shopId", shopId)
                .addSnapshotListener(listener);
    }
}
