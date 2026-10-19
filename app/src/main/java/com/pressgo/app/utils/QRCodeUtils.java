package com.pressgo.app.utils;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.EnumMap;
import java.util.Map;

/**
 * QR code generation utilities using ZXing / journeyapps BarcodeEncoder.
 */
public final class QRCodeUtils {

    private static final String TAG = "QRCodeUtils";

    private QRCodeUtils() {}

    // ─────────────────────────────────────────────────────────────────────────
    // QR code generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a QR code {@link Bitmap} for the given content string.
     *
     * <p>Uses {@link BarcodeEncoder} from the journeyapps wrapper so the
     * rendering step (BitMatrix → Bitmap) is handled for us.
     *
     * @param content The string to encode (e.g. an order ID or URL)
     * @param width   Desired bitmap width in pixels
     * @param height  Desired bitmap height in pixels
     * @return A QR code {@link Bitmap}, or {@code null} on encoding failure
     */
    public static Bitmap generateQRCode(String content, int width, int height) {
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "generateQRCode: content is null or empty");
            return null;
        }
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "generateQRCode: invalid dimensions " + width + "x" + height);
            return null;
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BarcodeEncoder encoder = new BarcodeEncoder();
            BitMatrix bitMatrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            return encoder.createBitmap(bitMatrix);
        } catch (WriterException e) {
            Log.e(TAG, "generateQRCode: encoding failed for content=" + content, e);
            return null;
        }
    }
}
