package com.pressgo.app.utils;

import java.util.regex.Pattern;

/**
 * Input validation utilities used across login, registration, and order forms.
 */
public final class ValidationUtils {

    // RFC-5322 simplified email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

    // E.164 / common phone pattern (7–15 digits, optional leading +)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9]{7,15}$"
    );

    // Page-range pattern: "all", single page, range, or comma-separated
    private static final Pattern PAGE_RANGE_PATTERN = Pattern.compile(
            "^(all|\\d+(-\\d+)?(,\\d+(-\\d+)?)*)$",
            Pattern.CASE_INSENSITIVE
    );

    private ValidationUtils() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Field validators
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @return {@code true} if {@code email} is non-null and matches a valid
     *         email address pattern.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Password must be at least 8 characters long.
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    /**
     * Accepts E.164 and common national phone formats (7–15 digits).
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return false;
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Name must be non-null, non-blank, and between 2 and 60 characters.
     */
    public static boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 60;
    }

    /**
     * Copy count must be between 1 and 999 inclusive.
     */
    public static boolean isValidCopies(int copies) {
        return copies >= 1 && copies <= 999;
    }

    /**
     * Validates a page-range string such as {@code "all"}, {@code "1-5"},
     * or {@code "1,3,5-7"}.
     */
    public static boolean isValidPageRange(String pageRange) {
        if (pageRange == null || pageRange.trim().isEmpty()) return false;
        return PAGE_RANGE_PATTERN.matcher(pageRange.trim()).matches();
    }

    /**
     * Budget must be a positive finite value.
     */
    public static boolean isValidBudget(double budget) {
        return Double.isFinite(budget) && budget > 0;
    }
}
