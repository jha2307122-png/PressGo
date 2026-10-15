package com.pressgo.app.repositories;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.pressgo.app.PressGoApplication;
import com.pressgo.app.models.Shop;
import com.pressgo.app.models.ShopPricing;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles read/write operations for shops and shop pricing with Firestore & local persistence.
 */
public class ShopRepository {
    private static final String TAG = "ShopRepository";

    private static final String COLLECTION_SHOPS        = "shops";
    private static final String COLLECTION_SHOP_PRICING = "shopPricing";
    private static final String PREF_SHOPS_CACHE        = "registered_shops_cache";
    private static final String PREF_PRICING_CACHE      = "registered_pricing_cache";
    private static final String KEY_SHOPS               = "shops_list";

    private static ShopRepository instance;

    private final FirebaseFirestore db;
    private final Gson gson;

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private ShopRepository() {
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

    public static synchronized ShopRepository getInstance() {
        if (instance == null) {
            instance = new ShopRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Local Shop Cache
    // -------------------------------------------------------------------------

    public synchronized void saveLocalShop(Shop shop) {
        if (shop == null || shop.getId() == null) return;
        Context ctx = PressGoApplication.getInstance();
        if (ctx == null) return;

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_SHOPS_CACHE, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_SHOPS, null);
            Map<String, Shop> map = new LinkedHashMap<>();
            if (json != null && !json.isEmpty()) {
                Type type = new TypeToken<List<Shop>>(){}.getType();
                List<Shop> list = gson.fromJson(json, type);
                if (list != null) {
                    for (Shop s : list) {
                        if (s.getId() != null) map.put(s.getId(), s);
                    }
                }
            }
            map.put(shop.getId(), shop);
            prefs.edit().putString(KEY_SHOPS, gson.toJson(new ArrayList<>(map.values()))).apply();
            Log.d(TAG, "Shop saved to local cache: " + shop.getName() + " (" + shop.getId() + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error saving local shop", e);
        }
    }

    public synchronized List<Shop> getLocalRegisteredShops() {
        List<Shop> result = new ArrayList<>();
        Context ctx = PressGoApplication.getInstance();
        if (ctx == null) return result;

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_SHOPS_CACHE, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_SHOPS, null);
            if (json != null && !json.isEmpty()) {
                Type type = new TypeToken<List<Shop>>(){}.getType();
                List<Shop> list = gson.fromJson(json, type);
                if (list != null) result.addAll(list);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading local registered shops", e);
        }
        return result;
    }

    public synchronized void saveLocalPricing(ShopPricing pricing) {
        if (pricing == null || pricing.getShopId() == null) return;
        Context ctx = PressGoApplication.getInstance();
        if (ctx == null) return;

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_PRICING_CACHE, Context.MODE_PRIVATE);
            prefs.edit().putString(pricing.getShopId(), gson.toJson(pricing)).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving local pricing", e);
        }
    }

    public synchronized ShopPricing getLocalPricing(String shopId) {
        if (shopId == null) return null;
        Context ctx = PressGoApplication.getInstance();
        if (ctx == null) return null;

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_PRICING_CACHE, Context.MODE_PRIVATE);
            String json = prefs.getString(shopId, null);
            if (json != null && !json.isEmpty()) {
                return gson.fromJson(json, ShopPricing.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading local pricing", e);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Shop operations
    // -------------------------------------------------------------------------

    /**
     * Retrieves all active shops.
     * Combines locally registered shops, Firestore registered shops, and campus default hubs.
     */
    public void getAllActiveShops(OnSuccessListener<List<Shop>> successListener,
                                  OnFailureListener failureListener) {
        Map<String, Shop> combined = new LinkedHashMap<>();

        // 1. Put local registered shops first
        for (Shop localShop : getLocalRegisteredShops()) {
            if (localShop.getId() != null) {
                combined.put(localShop.getId(), localShop);
            }
        }

        // 2. Fetch from Firestore
        db.collection(COLLECTION_SHOPS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Shop shop = doc.toObject(Shop.class);
                            if (shop != null) {
                                if (shop.getId() == null || shop.getId().isEmpty()) {
                                    shop.setId(doc.getId());
                                }
                                Boolean active = doc.getBoolean("isActive");
                                if (active == null) active = doc.getBoolean("active");
                                if (active != null) shop.setActive(active);
                                else shop.setActive(true);

                                Boolean open = doc.getBoolean("isOpen");
                                if (open == null) open = doc.getBoolean("open");
                                if (open != null) shop.setOpen(open);

                                Boolean localOverride = getLocalShopOpenOverride(shop.getId());
                                if (localOverride != null) shop.setOpen(localOverride);

                                if (shop.isActive()) {
                                    combined.put(shop.getId(), shop);
                                    saveLocalShop(shop);
                                }
                            }
                        }
                    }

                    // 3. Include default campus shops if not already present
                    for (Shop defShop : getDefaultCampusShops()) {
                        if (!combined.containsKey(defShop.getId())) {
                            Boolean localOverride = getLocalShopOpenOverride(defShop.getId());
                            if (localOverride != null) defShop.setOpen(localOverride);
                            combined.put(defShop.getId(), defShop);
                        }
                    }

                    if (successListener != null) {
                        successListener.onSuccess(new ArrayList<>(combined.values()));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore shops fetch failed, falling back to local + defaults: " + e.getMessage());
                    for (Shop defShop : getDefaultCampusShops()) {
                        if (!combined.containsKey(defShop.getId())) {
                            Boolean localOverride = getLocalShopOpenOverride(defShop.getId());
                            if (localOverride != null) defShop.setOpen(localOverride);
                            combined.put(defShop.getId(), defShop);
                        }
                    }
                    if (successListener != null) {
                        successListener.onSuccess(new ArrayList<>(combined.values()));
                    } else if (failureListener != null) {
                        failureListener.onFailure(e);
                    }
                });
    }

    /**
     * Retrieves a single shop by its document ID.
     */
    public void getShopById(String shopId,
                            OnSuccessListener<Shop> successListener,
                            OnFailureListener failureListener) {
        db.collection(COLLECTION_SHOPS)
                .document(shopId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Shop shop = documentSnapshot.toObject(Shop.class);
                    if (shop != null) {
                        if (shop.getId() == null || shop.getId().isEmpty()) {
                            shop.setId(documentSnapshot.getId());
                        }
                        Boolean active = documentSnapshot.getBoolean("isActive");
                        if (active == null) active = documentSnapshot.getBoolean("active");
                        if (active != null) shop.setActive(active);

                        Boolean open = documentSnapshot.getBoolean("isOpen");
                        if (open == null) open = documentSnapshot.getBoolean("open");
                        if (open != null) shop.setOpen(open);

                        Boolean localOverride = getLocalShopOpenOverride(shop.getId());
                        if (localOverride != null) shop.setOpen(localOverride);

                        saveLocalShop(shop);
                        if (successListener != null) successListener.onSuccess(shop);
                    } else {
                        Shop foundLocal = null;
                        for (Shop s : getLocalRegisteredShops()) {
                            if (shopId != null && shopId.equals(s.getId())) {
                                foundLocal = s;
                                break;
                            }
                        }
                        if (foundLocal == null) {
                            foundLocal = getDefaultShopById(shopId);
                        }
                        if (foundLocal != null) {
                            Boolean localOverride = getLocalShopOpenOverride(foundLocal.getId());
                            if (localOverride != null) foundLocal.setOpen(localOverride);
                        }
                        if (successListener != null) successListener.onSuccess(foundLocal);
                    }
                })
                .addOnFailureListener(e -> {
                    Shop fallback = null;
                    for (Shop s : getLocalRegisteredShops()) {
                        if (shopId != null && shopId.equals(s.getId())) {
                            fallback = s;
                            break;
                        }
                    }
                    if (fallback == null) fallback = getDefaultShopById(shopId);
                    if (fallback != null) {
                        Boolean localOverride = getLocalShopOpenOverride(fallback.getId());
                        if (localOverride != null) fallback.setOpen(localOverride);
                        if (successListener != null) successListener.onSuccess(fallback);
                    } else if (failureListener != null) {
                        failureListener.onFailure(e);
                    }
                });
    }

    public synchronized Boolean getLocalShopOpenOverride(String shopId) {
        if (shopId == null) return null;
        Context ctx = PressGoApplication.getInstance();
        if (ctx == null) return null;
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF_SHOPS_CACHE, Context.MODE_PRIVATE);
            String key = "shop_open_" + shopId;
            if (prefs.contains(key)) {
                return prefs.getBoolean(key, true);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public synchronized void updateShopOpenStatus(String shopId, boolean isOpen, OnCompleteListener<Void> onComplete) {
        if (shopId == null || shopId.isEmpty()) return;

        Context ctx = PressGoApplication.getInstance();
        if (ctx != null) {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(PREF_SHOPS_CACHE, Context.MODE_PRIVATE);
                prefs.edit().putBoolean("shop_open_" + shopId, isOpen).apply();
            } catch (Exception ignored) {}
        }

        List<Shop> localList = getLocalRegisteredShops();
        for (Shop s : localList) {
            if (shopId.equals(s.getId())) {
                s.setOpen(isOpen);
                saveLocalShop(s);
                break;
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOpen", isOpen);
        updates.put("open", isOpen);
        db.collection(COLLECTION_SHOPS).document(shopId)
                .update(updates)
                .addOnCompleteListener(t -> {
                    if (onComplete != null) onComplete.onComplete(t);
                });
    }

    public ListenerRegistration addShopListener(String shopId,
                                                EventListener<DocumentSnapshot> listener) {
        return db.collection(COLLECTION_SHOPS)
                .document(shopId)
                .addSnapshotListener(listener);
    }

    /**
     * Retrieves the pricing configuration for a specific shop.
     */
    public void getShopPricing(String shopId,
                               OnSuccessListener<ShopPricing> successListener,
                               OnFailureListener failureListener) {
        // Check local pricing cache first
        ShopPricing local = getLocalPricing(shopId);
        if (local != null && successListener != null) {
            successListener.onSuccess(local);
            return;
        }

        db.collection(COLLECTION_SHOP_PRICING)
                .document(shopId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    ShopPricing pricing = documentSnapshot.toObject(ShopPricing.class);
                    if (pricing != null) {
                        saveLocalPricing(pricing);
                        if (successListener != null) successListener.onSuccess(pricing);
                    } else {
                        // Check alternate collection name "shop_pricing"
                        db.collection("shop_pricing").document(shopId).get()
                                .addOnSuccessListener(altDoc -> {
                                    ShopPricing altPricing = altDoc.toObject(ShopPricing.class);
                                    if (altPricing != null) {
                                        saveLocalPricing(altPricing);
                                        if (successListener != null) successListener.onSuccess(altPricing);
                                    } else {
                                        ShopPricing def = getDefaultPricingForShop(shopId);
                                        if (successListener != null) successListener.onSuccess(def);
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    ShopPricing def = getDefaultPricingForShop(shopId);
                                    if (successListener != null) successListener.onSuccess(def);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    ShopPricing def = getDefaultPricingForShop(shopId);
                    if (successListener != null) {
                        successListener.onSuccess(def);
                    } else if (failureListener != null) {
                        failureListener.onFailure(e);
                    }
                });
    }

    public void saveShopPricing(ShopPricing pricing, OnCompleteListener<Void> onComplete) {
        if (pricing == null || pricing.getShopId() == null) return;
        saveLocalPricing(pricing);

        // Write to both "shopPricing" and "shop_pricing" to maintain absolute consistency
        db.collection(COLLECTION_SHOP_PRICING).document(pricing.getShopId())
                .set(pricing)
                .addOnCompleteListener(t -> {
                    try {
                        db.collection("shop_pricing").document(pricing.getShopId()).set(pricing);
                    } catch (Exception ignored) {}
                    if (onComplete != null) onComplete.onComplete(t);
                });
    }

    public ListenerRegistration addShopPricingListener(String shopId,
                                                       EventListener<DocumentSnapshot> listener) {
        return db.collection(COLLECTION_SHOP_PRICING)
                .document(shopId)
                .addSnapshotListener(listener);
    }

    public void searchShops(String query,
                            OnSuccessListener<List<Shop>> successListener,
                            OnFailureListener failureListener) {
        getAllActiveShops(all -> {
            if (query == null || query.trim().isEmpty()) {
                if (successListener != null) successListener.onSuccess(all);
                return;
            }
            String q = query.toLowerCase().trim();
            List<Shop> filtered = new ArrayList<>();
            for (Shop s : all) {
                String name = s.getName() != null ? s.getName().toLowerCase() : "";
                String address = s.getAddress() != null ? s.getAddress().toLowerCase() : "";
                if (name.contains(q) || address.contains(q)) {
                    filtered.add(s);
                }
            }
            if (successListener != null) successListener.onSuccess(filtered);
        }, failureListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Default Campus Shops (Pre-configured for immediate availability)
    // ─────────────────────────────────────────────────────────────────────────

    public static List<Shop> getDefaultCampusShops() {
        List<Shop> list = new ArrayList<>();

        Shop s1 = new Shop();
        s1.setId("shop_campus_central");
        s1.setName("Campus Central Print & Copy");
        s1.setAddress("Student Center, Ground Floor, Room 102");
        s1.setPhone("+1 (555) 019-2831");
        s1.setEmail("centralprint@campus.edu");
        s1.setDescription("High-speed printing, thesis & spiral binding, color copies, and laminations.");
        s1.setOpeningHours("08:00 AM");
        s1.setClosingHours("08:00 PM");
        s1.setOpen(true);
        s1.setActive(true);
        s1.setRating(4.9);
        s1.setTotalRatings(128);
        s1.setEstimatedQueueMinutes(5);
        s1.setHasColorPrinting(true);
        s1.setHasDuplexPrinting(true);
        s1.setHasBinding(true);
        s1.setHasStapling(true);
        s1.setHasLamination(true);
        s1.setLatitude(23.8103);
        s1.setLongitude(90.4125);
        s1.setSupportedFileTypes(Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "jpg", "png"));
        s1.setAvailablePaperSizes(Arrays.asList("A4", "A3", "Letter"));
        s1.setCreatedAt(Timestamp.now());
        list.add(s1);

        Shop s2 = new Shop();
        s2.setId("shop_library_hub");
        s2.setName("Library Digital Print Hub");
        s2.setAddress("Central Library, 2nd Floor, Room 204");
        s2.setPhone("+1 (555) 019-2832");
        s2.setEmail("libraryprint@campus.edu");
        s2.setDescription("Quiet academic printing center, research papers, notes, and thesis binding.");
        s2.setOpeningHours("09:00 AM");
        s2.setClosingHours("10:00 PM");
        s2.setOpen(true);
        s2.setActive(true);
        s2.setRating(4.8);
        s2.setTotalRatings(94);
        s2.setEstimatedQueueMinutes(3);
        s2.setHasColorPrinting(true);
        s2.setHasDuplexPrinting(true);
        s2.setHasBinding(true);
        s2.setHasStapling(true);
        s2.setHasLamination(false);
        s2.setLatitude(23.8115);
        s2.setLongitude(90.4138);
        s2.setSupportedFileTypes(Arrays.asList("pdf", "doc", "docx", "jpg", "png"));
        s2.setAvailablePaperSizes(Arrays.asList("A4", "Letter"));
        s2.setCreatedAt(Timestamp.now());
        list.add(s2);

        Shop s3 = new Shop();
        s3.setId("shop_engineering_print");
        s3.setName("Engineering & Tech Print Center");
        s3.setAddress("Engineering Complex, Building 4, Annex 1");
        s3.setPhone("+1 (555) 019-2833");
        s3.setEmail("techprint@campus.edu");
        s3.setDescription("Large format CAD drawings, project reports, color posters, and gloss prints.");
        s3.setOpeningHours("08:30 AM");
        s3.setClosingHours("07:00 PM");
        s3.setOpen(true);
        s3.setActive(true);
        s3.setRating(4.7);
        s3.setTotalRatings(76);
        s3.setEstimatedQueueMinutes(8);
        s3.setHasColorPrinting(true);
        s3.setHasDuplexPrinting(true);
        s3.setHasBinding(true);
        s3.setHasStapling(true);
        s3.setHasLamination(true);
        s3.setLatitude(23.8090);
        s3.setLongitude(90.4110);
        s3.setSupportedFileTypes(Arrays.asList("pdf", "dwg", "docx", "png", "jpg"));
        s3.setAvailablePaperSizes(Arrays.asList("A4", "A3", "Letter"));
        s3.setCreatedAt(Timestamp.now());
        list.add(s3);

        Shop s4 = new Shop();
        s4.setId("shop_hostel_express");
        s4.setName("Hostel Express Print Point");
        s4.setAddress("North Residential Hall, Convenience Block");
        s4.setPhone("+1 (555) 019-2834");
        s4.setEmail("expressprint@campus.edu");
        s4.setDescription("Fast document printing for assignments, lecture slides, and past papers.");
        s4.setOpeningHours("07:00 AM");
        s4.setClosingHours("11:00 PM");
        s4.setOpen(true);
        s4.setActive(true);
        s4.setRating(4.6);
        s4.setTotalRatings(53);
        s4.setEstimatedQueueMinutes(2);
        s4.setHasColorPrinting(true);
        s4.setHasDuplexPrinting(true);
        s4.setHasBinding(false);
        s4.setHasStapling(true);
        s4.setHasLamination(false);
        s4.setLatitude(23.8130);
        s4.setLongitude(90.4150);
        s4.setSupportedFileTypes(Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "png", "jpg"));
        s4.setAvailablePaperSizes(Arrays.asList("A4", "Letter"));
        s4.setCreatedAt(Timestamp.now());
        list.add(s4);

        return list;
    }

    private Shop getDefaultShopById(String shopId) {
        for (Shop s : getDefaultCampusShops()) {
            if (s.getId().equals(shopId)) {
                return s;
            }
        }
        return null;
    }

    public static ShopPricing getDefaultPricingForShop(String shopId) {
        ShopPricing pricing = new ShopPricing();
        pricing.setId(shopId);
        pricing.setShopId(shopId);
        pricing.setBwPageRate(0.10);
        pricing.setColorPageRate(0.50);
        pricing.setA4Multiplier(1.0);
        pricing.setA3Multiplier(1.5);
        pricing.setLetterMultiplier(1.0);
        pricing.setDuplexDiscount(0.02);
        pricing.setBindingCharge(1.50);
        pricing.setStaplingCharge(0.25);
        pricing.setLaminationCharge(1.00);
        pricing.setPriorityCharge(2.00);
        pricing.setMinimumOrder(0.50);
        pricing.setUpdatedAt(Timestamp.now());
        return pricing;
    }
}
