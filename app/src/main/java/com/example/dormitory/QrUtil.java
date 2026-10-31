package com.example.dormitory;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QrUtil {
    /** QR on a white card with the visitor's name and expiry underneath, ready to send as an image. */
    public static Bitmap card(String text, String name, String footer) {
        Bitmap qr = make(text, 720);
        if (qr == null) return null;
        int pad = 60, w = 720 + pad * 2, h = 720 + pad * 2 + 170;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(qr, pad, pad, null);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        p.setColor(Color.parseColor("#09090B"));
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextSize(52f);
        c.drawText(name, w / 2f, 720 + pad * 2 + 30, p);
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setColor(Color.parseColor("#71717A"));
        p.setTextSize(34f);
        c.drawText(footer, w / 2f, 720 + pad * 2 + 90, p);
        c.drawText("Show this QR to the guard at the gate", w / 2f, 720 + pad * 2 + 140, p);
        return out;
    }

    public static Bitmap make(String text, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    px[y * size + x] = m.get(x, y) ? Color.parseColor("#1E1B4B") : Color.WHITE;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bmp.setPixels(px, 0, size, 0, 0, size, size);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
