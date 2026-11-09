package com.pressgo.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Utility for building and launching Implicit Intents across PressGo.
 * Demonstrates systematic usage of Android Implicit Intents:
 * - Intent.ACTION_VIEW (Web URLs, Maps, External Document Viewer)
 * - Intent.ACTION_DIAL (Phone Dialer)
 * - Intent.ACTION_SEND (Sharing Text, Receipts, and Documents)
 * - Intent.ACTION_SENDTO (Email)
 * (Week 8: Implicit Intents).
 */
public final class ImplicitIntentUtils {

    private static final String TAG = "ImplicitIntentUtils";

    private ImplicitIntentUtils() {}

    /**
     * Implicit Intent: Opens the phone dialer with the shop's contact number pre-filled.
     */
    public static void openDialer(Context context, String phoneNumber) {
        if (context == null || phoneNumber == null || phoneNumber.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phoneNumber.trim()));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching dialer implicit intent", e);
            Toast.makeText(context, "No dialer application found", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Implicit Intent: Opens the device's map application (Google Maps, OpenStreetMap, etc.) to view coordinates.
     */
    public static void openMapLocation(Context context, double lat, double lng, String label) {
        if (context == null) return;
        try {
            String uriStr = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + Uri.encode(label != null ? label : "Print Shop") + ")";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening map implicit intent", e);
            Toast.makeText(context, "No map application available", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Implicit Intent: Opens a web URL in the device's default web browser.
     */
    public static void openWebUrl(Context context, String url) {
        if (context == null || url == null) return;
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening browser implicit intent", e);
            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Implicit Intent: Shares plain text (e.g. order receipt or tracking info) to any app supporting ACTION_SEND.
     */
    public static void sharePlainText(Context context, String chooserTitle, String textToShare) {
        if (context == null || textToShare == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, textToShare);
            Intent chooser = Intent.createChooser(intent, chooserTitle != null ? chooserTitle : "Share with");
            context.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Error launching share text implicit intent", e);
        }
    }

    /**
     * Implicit Intent: Option A - WhatsApp Sharing.
     * Uses ACTION_SEND with MIME type text/plain directed to WhatsApp if installed.
     * Does not automatically send messages (user reviews and confirms send in WhatsApp).
     */
    public static void shareViaWhatsApp(Context context, String message) {
        if (context == null || message == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.setPackage("com.whatsapp");
            intent.putExtra(Intent.EXTRA_TEXT, message);
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e1) {
            // Check WhatsApp Business as alternative
            try {
                Intent bIntent = new Intent(Intent.ACTION_SEND);
                bIntent.setType("text/plain");
                bIntent.setPackage("com.whatsapp.w4b");
                bIntent.putExtra(Intent.EXTRA_TEXT, message);
                context.startActivity(bIntent);
            } catch (android.content.ActivityNotFoundException e2) {
                Toast.makeText(context, "WhatsApp is not installed. Opening app chooser instead.", Toast.LENGTH_SHORT).show();
                sharePlainText(context, "Share Pickup Authorization via", message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sharing via WhatsApp", e);
            sharePlainText(context, "Share Pickup Authorization via", message);
        }
    }

    /**
     * Implicit Intent: Option B - SMS Sharing.
     * Uses ACTION_SENDTO with an sms: URI to prefill contact phone and message body.
     * Lets the user review and send the SMS in their chosen SMS app without SEND_SMS permission.
     */
    public static void shareViaSms(Context context, String phoneNumber, String message) {
        if (context == null || message == null) return;
        try {
            String cleanNumber = phoneNumber != null ? phoneNumber.replaceAll("[^0-9+]", "") : "";
            Uri uri = Uri.parse("sms:" + Uri.encode(cleanNumber));
            Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
            intent.putExtra("sms_body", message);
            intent.putExtra(Intent.EXTRA_TEXT, message);
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(context, "No SMS app found. Opening share sheet.", Toast.LENGTH_SHORT).show();
            sharePlainText(context, "Share Pickup Authorization via", message);
        } catch (Exception e) {
            Log.e(TAG, "Error opening SMS composer", e);
            sharePlainText(context, "Share Pickup Authorization via", message);
        }
    }

    /**
     * Generates a professional pickup authorization message using actual customer,
     * contact, and order details.
     */
    public static String buildPickupAuthorizationMessage(String contactName,
                                                        String customerName,
                                                        String orderId,
                                                        String shopName) {
        String cName = (contactName != null && !contactName.trim().isEmpty()) ? contactName.trim() : "Campus Peer";
        String uName = (customerName != null && !customerName.trim().isEmpty()) ? customerName.trim() : "PressGo Customer";
        String sName = (shopName != null && !shopName.trim().isEmpty()) ? shopName.trim() : "PressGo Campus Shop";
        String oId   = (orderId != null && !orderId.trim().isEmpty()) ? orderId.trim() : "N/A";

        return "Hello " + cName + ",\n\n" +
                "I have authorized you to collect my printing order from PressGo.\n\n" +
                "Order ID: " + oId + "\n" +
                "Pickup Location: " + sName + "\n" +
                "Customer: " + uName + "\n" +
                "Pickup Authorization: Authorized by the customer.\n\n" +
                "Please show this message and follow the shop's verification procedure when collecting the order.\n\n" +
                "Thank you,\n" +
                uName;
    }

    /**
     * Implicit Intent: Shares a local file (e.g., converted PDF or document) using FileProvider.
     */
    public static void shareFile(Context context, File file, String mimeType, String chooserTitle) {
        if (context == null || file == null || !file.exists()) {
            Log.w(TAG, "shareFile: File does not exist");
            return;
        }
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType != null ? mimeType : "*/*");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, chooserTitle != null ? chooserTitle : "Share Document");
            context.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file implicit intent", e);
        }
    }

    /**
     * Implicit Intent: Opens an external viewer for a file (e.g., PDF viewer, image viewer, Office reader).
     */
    public static void viewExternalFile(Context context, File file, String mimeType) {
        if (context == null || file == null || !file.exists()) return;
        FileDownloadUtils.openFile(context, file);
    }

    /**
     * Implicit Intent: Composes an email with ACTION_SENDTO.
     */
    public static void composeEmail(Context context, String recipient, String subject, String body) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            if (recipient != null) intent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
            if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            if (body != null) intent.putExtra(Intent.EXTRA_TEXT, body);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching email implicit intent", e);
            Toast.makeText(context, "No email client installed", Toast.LENGTH_SHORT).show();
        }
    }
}
