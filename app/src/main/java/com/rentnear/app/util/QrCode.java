package com.rentnear.app.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

/** Generates QR code bitmaps with ZXing. */
public final class QrCode {
    private static final String PREFIX = "rentnear";

    private QrCode() {}

    /** QR content: rentnear|bookingId|kind|code, where kind is "handover" or "return". */
    public static String payload(String bookingId, String kind, String code) {
        return PREFIX + "|" + bookingId + "|" + kind + "|" + code;
    }

    /** @return the code if the scanned text is a RentNear QR for this booking and kind, else null */
    public static String parse(String text, String bookingId, String kind) {
        if (text == null) return null;
        String[] p = text.split("\\|");
        if (p.length != 4 || !PREFIX.equals(p[0]) || !p[1].equals(bookingId) || !p[2].equals(kind)) {
            return null;
        }
        return p[3];
    }

    public static Bitmap generate(String text, int sizePx) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        int w = m.getWidth();
        int h = m.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, w, 0, 0, w, h);
        return bmp;
    }
}
