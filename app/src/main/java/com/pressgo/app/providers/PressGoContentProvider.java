package com.pressgo.app.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Android ContentProvider for PressGo.
 * Exposes internal application data (Cached Print Orders and App Metadata)
 * using standard Android ContentProvider architecture.
 * (Week 10: Content Providers).
 */
public class PressGoContentProvider extends ContentProvider {

    private static final String TAG = "PressGoContentProvider";

    public static final String AUTHORITY = "com.pressgo.app.provider";
    public static final Uri CONTENT_URI_ORDERS = Uri.parse("content://" + AUTHORITY + "/orders");
    public static final Uri CONTENT_URI_APP_INFO = Uri.parse("content://" + AUTHORITY + "/app_info");

    // Table columns
    public static final String COL_ID = "_id";
    public static final String COL_ORDER_ID = "order_id";
    public static final String COL_CUSTOMER_ID = "customer_id";
    public static final String COL_SHOP_ID = "shop_id";
    public static final String COL_STATUS = "status";
    public static final String COL_TOTAL_AMOUNT = "total_amount";
    public static final String COL_TIMESTAMP = "timestamp";

    private static final String[] ORDER_COLUMNS = new String[]{
            COL_ID, COL_ORDER_ID, COL_CUSTOMER_ID, COL_SHOP_ID, COL_STATUS, COL_TOTAL_AMOUNT, COL_TIMESTAMP
    };

    // URI Matcher constants
    private static final int CODE_ORDERS = 100;
    private static final int CODE_ORDER_ID = 101;
    private static final int CODE_APP_INFO = 200;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, "orders", CODE_ORDERS);
        URI_MATCHER.addURI(AUTHORITY, "orders/#", CODE_ORDER_ID);
        URI_MATCHER.addURI(AUTHORITY, "app_info", CODE_APP_INFO);
    }

    // In-memory backing store for provider records
    private static final List<CachedOrderRecord> CACHED_ORDERS = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    public static class CachedOrderRecord {
        public long id;
        public String orderId;
        public String customerId;
        public String shopId;
        public String status;
        public double totalAmount;
        public long timestamp;

        public CachedOrderRecord(long id, String orderId, String customerId, String shopId,
                                 String status, double totalAmount, long timestamp) {
            this.id = id;
            this.orderId = orderId;
            this.customerId = customerId;
            this.shopId = shopId;
            this.status = status;
            this.totalAmount = totalAmount;
            this.timestamp = timestamp;
        }
    }

    @Override
    public boolean onCreate() {
        Log.i(TAG, "PressGoContentProvider initialized successfully");
        // Pre-populate with initial demonstration record
        if (CACHED_ORDERS.isEmpty()) {
            CACHED_ORDERS.add(new CachedOrderRecord(
                    ID_GENERATOR.getAndIncrement(),
                    "PG-DEMO-001",
                    "demo_cust_uid",
                    "demo_shop_01",
                    "READY_FOR_PICKUP",
                    45.0,
                    System.currentTimeMillis()
            ));
        }
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : ORDER_COLUMNS);
        int match = URI_MATCHER.match(uri);

        switch (match) {
            case CODE_ORDERS:
                synchronized (CACHED_ORDERS) {
                    for (CachedOrderRecord rec : CACHED_ORDERS) {
                        cursor.addRow(new Object[]{
                                rec.id, rec.orderId, rec.customerId, rec.shopId, rec.status, rec.totalAmount, rec.timestamp
                        });
                    }
                }
                break;

            case CODE_ORDER_ID:
                long reqId = ContentUris.parseId(uri);
                synchronized (CACHED_ORDERS) {
                    for (CachedOrderRecord rec : CACHED_ORDERS) {
                        if (rec.id == reqId) {
                            cursor.addRow(new Object[]{
                                    rec.id, rec.orderId, rec.customerId, rec.shopId, rec.status, rec.totalAmount, rec.timestamp
                            });
                            break;
                        }
                    }
                }
                break;

            case CODE_APP_INFO:
                MatrixCursor infoCursor = new MatrixCursor(new String[]{"app_name", "version", "status"});
                infoCursor.addRow(new Object[]{"PressGo", "2.0.0", "ONLINE"});
                if (getContext() != null) {
                    infoCursor.setNotificationUri(getContext().getContentResolver(), uri);
                }
                return infoCursor;

            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (URI_MATCHER.match(uri) != CODE_ORDERS) {
            throw new IllegalArgumentException("Unsupported URI for insertion: " + uri);
        }

        if (values == null) return null;

        long newId = ID_GENERATOR.getAndIncrement();
        String orderId = values.getAsString(COL_ORDER_ID);
        String customerId = values.getAsString(COL_CUSTOMER_ID);
        String shopId = values.getAsString(COL_SHOP_ID);
        String status = values.getAsString(COL_STATUS);
        Double amount = values.getAsDouble(COL_TOTAL_AMOUNT);
        Long ts = values.getAsLong(COL_TIMESTAMP);

        CachedOrderRecord newRec = new CachedOrderRecord(
                newId,
                orderId != null ? orderId : "PG-" + newId,
                customerId != null ? customerId : "anonymous",
                shopId != null ? shopId : "default_shop",
                status != null ? status : "PENDING",
                amount != null ? amount : 0.0,
                ts != null ? ts : System.currentTimeMillis()
        );

        CACHED_ORDERS.add(newRec);

        Uri resultUri = ContentUris.withAppendedId(CONTENT_URI_ORDERS, newId);
        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(resultUri, null);
        }
        return resultUri;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        int match = URI_MATCHER.match(uri);
        int updated = 0;

        if (values == null) return 0;

        if (match == CODE_ORDER_ID) {
            long reqId = ContentUris.parseId(uri);
            synchronized (CACHED_ORDERS) {
                for (CachedOrderRecord rec : CACHED_ORDERS) {
                    if (rec.id == reqId) {
                        if (values.containsKey(COL_STATUS)) rec.status = values.getAsString(COL_STATUS);
                        if (values.containsKey(COL_TOTAL_AMOUNT)) rec.totalAmount = values.getAsDouble(COL_TOTAL_AMOUNT);
                        updated++;
                        break;
                    }
                }
            }
        } else if (match == CODE_ORDERS) {
            String targetOrderId = values.getAsString(COL_ORDER_ID);
            if (targetOrderId != null) {
                synchronized (CACHED_ORDERS) {
                    for (CachedOrderRecord rec : CACHED_ORDERS) {
                        if (targetOrderId.equals(rec.orderId)) {
                            if (values.containsKey(COL_STATUS)) rec.status = values.getAsString(COL_STATUS);
                            if (values.containsKey(COL_TOTAL_AMOUNT)) rec.totalAmount = values.getAsDouble(COL_TOTAL_AMOUNT);
                            updated++;
                        }
                    }
                }
            }
        }

        if (updated > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return updated;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int match = URI_MATCHER.match(uri);
        int deleted = 0;

        if (match == CODE_ORDER_ID) {
            long reqId = ContentUris.parseId(uri);
            synchronized (CACHED_ORDERS) {
                for (int i = CACHED_ORDERS.size() - 1; i >= 0; i--) {
                    if (CACHED_ORDERS.get(i).id == reqId) {
                        CACHED_ORDERS.remove(i);
                        deleted++;
                        break;
                    }
                }
            }
        } else if (match == CODE_ORDERS) {
            deleted = CACHED_ORDERS.size();
            CACHED_ORDERS.clear();
        }

        if (deleted > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return deleted;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case CODE_ORDERS:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".order";
            case CODE_ORDER_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".order";
            case CODE_APP_INFO:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".app_info";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }
}
