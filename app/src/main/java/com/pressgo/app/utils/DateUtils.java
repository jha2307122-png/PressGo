package com.pressgo.app.utils;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Date / time formatting utilities for the PressGo UI.
 */
public final class DateUtils {

    private static final String FORMAT_DATE_TIME = "dd MMM yyyy, hh:mm a";
    private static final String FORMAT_DATE      = "dd MMM yyyy";
    private static final String FORMAT_TIME      = "hh:mm a";

    private DateUtils() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Formatting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Formats a Firestore {@link Timestamp} to a full date-time string.
     *
     * @param ts Firestore Timestamp; returns "" if null
     */
    public static String formatTimestamp(Timestamp ts) {
        if (ts == null) return "";
        return formatDateTime(ts.toDate());
    }

    /**
     * Formats a {@link Date} to "dd MMM yyyy, hh:mm a" (e.g. "21 Oct 2026, 02:30 PM").
     */
    public static String formatDateTime(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_DATE_TIME, Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * Formats a {@link Date} to "dd MMM yyyy" (e.g. "21 Oct 2026").
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_DATE, Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * Formats a {@link Date} to "hh:mm a" (e.g. "02:30 PM").
     */
    public static String formatTime(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_TIME, Locale.getDefault());
        return sdf.format(date);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relative time ("X hours ago")
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable relative time string for the given Firestore
     * {@link Timestamp} (e.g. "Just now", "5 minutes ago", "2 hours ago").
     */
    public static String getTimeAgo(Timestamp ts) {
        if (ts == null) return "";
        long nowMs  = System.currentTimeMillis();
        long thenMs = ts.toDate().getTime();
        long diffMs = nowMs - thenMs;

        if (diffMs < 0) return formatTimestamp(ts); // future timestamp

        long seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        long hours   = TimeUnit.MILLISECONDS.toHours(diffMs);
        long days    = TimeUnit.MILLISECONDS.toDays(diffMs);

        if (seconds < 60)  return "Just now";
        if (minutes < 60)  return minutes + " minute" + plural(minutes) + " ago";
        if (hours   < 24)  return hours   + " hour"   + plural(hours)   + " ago";
        if (days    <  7)  return days    + " day"    + plural(days)    + " ago";

        long weeks = days / 7;
        if (weeks < 4)     return weeks   + " week"   + plural(weeks)   + " ago";

        long months = days / 30;
        if (months < 12)   return months  + " month"  + plural(months)  + " ago";

        long years = days / 365;
        return years + " year" + plural(years) + " ago";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Day boundary helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a {@link Date} representing the start of today (00:00:00.000).
     */
    public static Date getTodayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE,      0);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Returns a {@link Date} representing the end of today (23:59:59.999).
     */
    public static Date getTodayEnd() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE,      59);
        cal.set(Calendar.SECOND,      59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String plural(long count) {
        return count == 1 ? "" : "s";
    }
}
