package com.example.subscreenpoke;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.freeme.backscreen.BackScreenNative;

/**
 * Genera frames de 360x360 en RGB_565, el formato exacto que espera
 * libBackScreen2.so (360*360*2 = 259200 bytes por frame).
 */
public class Frames {

    private static final int W = BackScreenNative.PANEL_WIDTH;
    private static final int H = BackScreenNative.PANEL_HEIGHT;

    /** "Hola mundo" sobre fondo oscuro. */
    public static Bitmap helloWorld(String line1, String line2) {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.rgb(8, 12, 30));

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(6f);
        ring.setColor(Color.rgb(0, 200, 160));
        c.drawCircle(W / 2f, H / 2f, W / 2f - 12f, ring);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        text.setTextSize(46f);
        c.drawText(line1, W / 2f, H / 2f - 6f, text);

        text.setTextSize(26f);
        text.setColor(Color.rgb(0, 220, 175));
        c.drawText(line2, W / 2f, H / 2f + 40f, text);

        return bmp;
    }

    /** Barras de color, util para verificar orden de bytes y geometria. */
    public static Bitmap colorBars() {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        int[] colors = {
                Color.RED, Color.GREEN, Color.BLUE,
                Color.YELLOW, Color.CYAN, Color.MAGENTA,
                Color.WHITE, Color.BLACK,
        };
        Paint p = new Paint();
        float bar = H / (float) colors.length;
        for (int i = 0; i < colors.length; i++) {
            p.setColor(colors[i]);
            c.drawRect(0, i * bar, W, (i + 1) * bar, p);
        }
        return bmp;
    }
}
