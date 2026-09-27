package com.freeme.backscreen;

import android.graphics.Bitmap;
import android.util.Log;
import com.hxy.miniscreen.frame.FeatureOption;

/* JADX INFO: loaded from: classes12.dex */
public class BackScreenNative {
    public static final int SCREENCONTENT_MODULE_LENGTH = (FeatureOption.MINISCREEN_OUT_HEIGHT * FeatureOption.MINISCREEN_OUT_WIDTH) * 2;
    private static final String TAG = "BackScreenNative";

    public static native boolean native_isScreenOn();

    public static native int native_refreshScreen(Bitmap bitmap, int i);

    public static native int native_screenOff();

    public static native int native_screenOn();

    public static native int native_setBrightnessScreen(int i);

    static {
        try {
            System.loadLibrary("BackScreen2");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "error: " + e);
        }
    }
}
