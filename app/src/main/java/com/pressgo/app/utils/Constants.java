package com.pressgo.app.utils;

/**
 * Application-wide constants for PressGo.
 */
public final class Constants {

    // Prevent instantiation
    private Constants() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Firestore collection names
    // ─────────────────────────────────────────────────────────────────────────

    public static final String COLLECTION_USERS               = "users";
    public static final String COLLECTION_SHOPS               = "shops";
    public static final String COLLECTION_SHOP_PRICING        = "shop_pricing";
    public static final String COLLECTION_ORDERS              = "orders";
    public static final String COLLECTION_ORDER_FILES         = "order_files";
    public static final String COLLECTION_NOTIFICATIONS       = "notifications";
    public static final String COLLECTION_PRINTERS            = "printers";
    public static final String COLLECTION_INVENTORY           = "inventory";
    public static final String COLLECTION_PAYMENTS            = "payments";
    public static final String COLLECTION_REFUNDS             = "refunds";
    public static final String COLLECTION_CONVERSION_HISTORY  = "conversion_history";
    public static final String COLLECTION_COMPRESSION_HISTORY = "compression_history";

    // ─────────────────────────────────────────────────────────────────────────
    // Firebase Storage bucket paths
    // ─────────────────────────────────────────────────────────────────────────

    public static final String STORAGE_ORDERS      = "orders/";
    public static final String STORAGE_CONVERSIONS = "conversions/";
    public static final String STORAGE_PROFILES    = "profiles/";

    // ─────────────────────────────────────────────────────────────────────────
    // Intent extra keys
    // ─────────────────────────────────────────────────────────────────────────

    public static final String EXTRA_ORDER_ID        = "extra_order_id";
    public static final String EXTRA_SHOP_ID         = "extra_shop_id";
    public static final String EXTRA_USER_ID         = "extra_user_id";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";
    public static final String EXTRA_CONVERSION_ID   = "extra_conversion_id";

    // ─────────────────────────────────────────────────────────────────────────
    // User roles
    // ─────────────────────────────────────────────────────────────────────────

    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_STAFF    = "STAFF";
    public static final String ROLE_ADMIN    = "ADMIN";

    // ─────────────────────────────────────────────────────────────────────────
    // File constraints
    // ─────────────────────────────────────────────────────────────────────────

    /** Maximum allowed upload file size: 50 MB. */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

    /** File extensions supported for printing. */
    public static final String[] SUPPORTED_EXTENSIONS = {
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "jpg", "jpeg", "png"
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Notification channel IDs
    // ─────────────────────────────────────────────────────────────────────────

    public static final String CHANNEL_ORDER_UPDATES = "order_updates";
    public static final String CHANNEL_ADMIN_ALERTS  = "admin_alerts";
    public static final String CHANNEL_GENERAL       = "general";

    // ─────────────────────────────────────────────────────────────────────────
    // Currency
    // ─────────────────────────────────────────────────────────────────────────

    public static final String CURRENCY_SYMBOL = "BDT ";
    public static final String CURRENCY_CODE   = "BDT";

    // ─────────────────────────────────────────────────────────────────────────
    // SharedPreferences keys
    // ─────────────────────────────────────────────────────────────────────────

    public static final String PREF_USER_ROLE = "pref_user_role";
    public static final String PREF_USER_ID   = "pref_user_id";
    public static final String PREF_SHOP_ID   = "pref_shop_id";

    // ─────────────────────────────────────────────────────────────────────────
    // Activity request codes
    // ─────────────────────────────────────────────────────────────────────────

    public static final int REQUEST_OPEN_DOCUMENT = 100;
    public static final int REQUEST_CAMERA        = 101;

    // ─────────────────────────────────────────────────────────────────────────
    // Default print settings
    // ─────────────────────────────────────────────────────────────────────────

    public static final int    DEFAULT_COPIES         = 1;
    public static final String DEFAULT_PAPER_SIZE     = "A4";
    public static final String DEFAULT_COLOR_MODE     = "BLACK_WHITE";
    public static final String DEFAULT_PRINT_SIDE     = "SINGLE";
    public static final String DEFAULT_ORIENTATION    = "PORTRAIT";
    public static final String DEFAULT_BINDING_TYPE   = "NONE";
    public static final int    DEFAULT_PAGES_PER_SHEET = 1;
    public static final String DEFAULT_PAGE_RANGE     = "all";
}
