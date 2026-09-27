package com.freeme.backscreen;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Puente JNI a /system/lib64/libBackScreen2.so, la libreria del vendor que pinta
 * la pantalla trasera del KINGKONG 11.
 *
 * El paquete y el nombre de clase DEBEN ser exactamente estos: los simbolos que
 * exporta la .so son Java_com_freeme_backscreen_BackScreenNative_*. Si se renombra
 * la clase, loadLibrary funciona pero las llamadas fallan con UnsatisfiedLinkError.
 *
 * A diferencia de com.mediatek.hxymanager.IHxyService (que vive en framework.jar y
 * hace shadowing), esta clase vive solo en el dex de la app del vendor, asi que
 * podemos declarar nuestra propia copia sin conflicto.
 *
 * El panel es RGB565 de 360x360 => 259200 bytes por frame.
 */
public class BackScreenNative {

    private static final String TAG = "BackScreenNative";

    public static final int PANEL_WIDTH = 360;
    public static final int PANEL_HEIGHT = 360;
    public static final int SCREENCONTENT_MODULE_LENGTH = PANEL_WIDTH * PANEL_HEIGHT * 2;

    /** null si la libreria cargo bien; el mensaje de error si no. */
    public static final String LOAD_ERROR;

    static {
        String err = null;
        try {
            System.loadLibrary("BackScreen2");
        } catch (Throwable t) {
            err = t.toString();
            Log.e(TAG, "loadLibrary fallo: " + t);
        }
        LOAD_ERROR = err;
    }

    public static native boolean native_isScreenOn();

    public static native int native_refreshScreen(Bitmap bitmap, int length);

    public static native int native_screenOff();

    public static native int native_screenOn();

    public static native int native_setBrightnessScreen(int level);
}
