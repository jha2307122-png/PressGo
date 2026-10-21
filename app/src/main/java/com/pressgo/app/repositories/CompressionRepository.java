package com.pressgo.app.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.pressgo.app.PressGoApplication;
import com.pressgo.app.models.CompressionRecord;
import com.pressgo.app.utils.Constants;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CompressionRepository {
    private static final String TAG = "CompressionRepository";
    private static final String PREF_NAME = "compression_history_local";
    private static final String KEY_RECORDS = "records";

    private static CompressionRepository instance;
    private final FirebaseFirestore db;
    private final Gson gson;

    private CompressionRepository() {
        db = FirebaseFirestore.getInstance();
        gson = new GsonBuilder()
                .registerTypeAdapter(Timestamp.class, (JsonSerializer<Timestamp>) (src, typeOfSrc, context) -> {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("seconds", src.getSeconds());
                    obj.addProperty("nanoseconds", src.getNanoseconds());
                    return obj;
                })
                .registerTypeAdapter(Timestamp.class, (JsonDeserializer<Timestamp>) (json, typeOfT, context) -> {
                    try {
                        JsonObject obj = json.getAsJsonObject();
                        long sec = obj.has("seconds") ? obj.get("seconds").getAsLong() : 0;
                        int nano = obj.has("nanoseconds") ? obj.get("nanoseconds").getAsInt() : 0;
                        return new Timestamp(sec, nano);
                    } catch (Exception e) {
                        return Timestamp.now();
                    }
                })
                .create();
    }

    public static synchronized CompressionRepository getInstance() {
        if (instance == null) {
            instance = new CompressionRepository();
        }
        return instance;
    }

    public void saveCompressionRecord(CompressionRecord record, OnCompleteListener<Void> onComplete) {
        if (record.getId() == null || record.getId().isEmpty()) {
            record.setId(UUID.randomUUID().toString());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Timestamp.now());
        }

        // 1. Immediately persist to local cache so user never loses record
        saveToLocalCache(record);

        // 2. Persist to Firestore
        try {
            db.collection(Constants.COLLECTION_COMPRESSION_HISTORY).document(record.getId())
                    .set(record)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Compression record saved to Firestore: " + record.getId());
                        } else {
                            Log.w(TAG, "Firestore save failed, local cache preserved", task.getException());
                        }
                        if (onComplete != null) onComplete.onComplete(task);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error initiating Firestore save", e);
        }
    }

    public void getUserCompressions(String userId,
                                   OnSuccessListener<List<CompressionRecord>> onSuccess,
                                   OnFailureListener onFailure) {
        // First retrieve local records
        List<CompressionRecord> localList = getLocalRecords(userId);

        try {
            // Query without composite index (.orderBy) to guarantee success on all Firebase setups
            db.collection(Constants.COLLECTION_COMPRESSION_HISTORY)
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        Map<String, CompressionRecord> merged = new LinkedHashMap<>();
                        // Add local first
                        for (CompressionRecord r : localList) {
                            if (r.getId() != null) merged.put(r.getId(), r);
                        }
                        // Merge Firestore docs
                        if (queryDocumentSnapshots != null) {
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                CompressionRecord record = doc.toObject(CompressionRecord.class);
                                if (record != null) {
                                    record.setId(doc.getId());
                                    merged.put(record.getId(), record);
                                    saveToLocalCache(record);
                                }
                            }
                        }
                        List<CompressionRecord> list = new ArrayList<>(merged.values());
                        sortDescending(list);
                        if (onSuccess != null) onSuccess.onSuccess(list);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firestore fetch failed, returning local cache: " + e.getMessage());
                        sortDescending(localList);
                        if (onSuccess != null) {
                            onSuccess.onSuccess(localList);
                        } else if (onFailure != null) {
                            onFailure.onFailure(e);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Exception querying Firestore, returning local cache", e);
            sortDescending(localList);
            if (onSuccess != null) onSuccess.onSuccess(localList);
        }
    }

    public void deleteCompressionRecord(String id, OnCompleteListener<Void> onComplete) {
        deleteFromLocalCache(id);
        try {
            db.collection(Constants.COLLECTION_COMPRESSION_HISTORY).document(id)
                    .delete()
                    .addOnCompleteListener(onComplete);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting compression from Firestore", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Local SharedPreferences cache
    // ─────────────────────────────────────────────────────────────────────────

    private SharedPreferences getPrefs() {
        Context ctx = PressGoApplication.getInstance();
        return ctx != null ? ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) : null;
    }

    private synchronized List<CompressionRecord> getLocalRecords(String userId) {
        List<CompressionRecord> result = new ArrayList<>();
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return result;

        String json = prefs.getString(KEY_RECORDS, null);
        if (json != null && !json.isEmpty()) {
            try {
                Type listType = new TypeToken<List<CompressionRecord>>(){}.getType();
                List<CompressionRecord> all = gson.fromJson(json, listType);
                if (all != null) {
                    for (CompressionRecord r : all) {
                        if (userId == null || userId.equals(r.getUserId()) || "guest_user".equals(userId) || "demo_cust_uid".equals(userId)) {
                            result.add(r);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing local compression records", e);
            }
        }
        return result;
    }

    private synchronized void saveToLocalCache(CompressionRecord record) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null || record == null) return;

        try {
            String json = prefs.getString(KEY_RECORDS, null);
            Map<String, CompressionRecord> map = new LinkedHashMap<>();
            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<List<CompressionRecord>>(){}.getType();
                List<CompressionRecord> existing = gson.fromJson(json, listType);
                if (existing != null) {
                    for (CompressionRecord r : existing) {
                        if (r.getId() != null) map.put(r.getId(), r);
                    }
                }
            }
            map.put(record.getId(), record);
            prefs.edit().putString(KEY_RECORDS, gson.toJson(new ArrayList<>(map.values()))).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving local compression record", e);
        }
    }

    private synchronized void deleteFromLocalCache(String id) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null || id == null) return;

        try {
            String json = prefs.getString(KEY_RECORDS, null);
            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<List<CompressionRecord>>(){}.getType();
                List<CompressionRecord> existing = gson.fromJson(json, listType);
                if (existing != null) {
                    List<CompressionRecord> updated = new ArrayList<>();
                    for (CompressionRecord r : existing) {
                        if (!id.equals(r.getId())) updated.add(r);
                    }
                    prefs.edit().putString(KEY_RECORDS, gson.toJson(updated)).apply();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing local compression record", e);
        }
    }

    private void sortDescending(List<CompressionRecord> list) {
        Collections.sort(list, (a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
    }
}
