package com.example.subscreenpoke;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.graphics.Bitmap;
import com.freeme.backscreen.BackScreenNative;


/**
 * Primera app: habla con el servicio propietario de la pantalla trasera.
 *
 * Se bindea a com.android.hxyservice/.HxyService (exportado, sin permiso) y llama
 * handleKeycode(134, action). 134 = KEYCODE_MINISCREEN_ON, el mismo keycode al que se
 * suscribe MiniscreenFunction dentro del servicio (DIAGNOSTICO.md §8.bis).
 *
 * Escrita sin clases anonimas a proposito: d8 8.2.2 falla al dexar ciertas
 * clases anonimas generadas por javac reciente.
 */
public class MainActivity extends Activity
        implements View.OnClickListener, ServiceConnection, Runnable {

    private static final int KEYCODE_MINISCREEN_ON = 134;
    private static final String ACTION_SERVICE = "com.android.hxyservice.bindservice";
    private static final String PACKAGE_SERVICE = "com.android.hxyservice";
    private static final String BACK_SCREEN_KEY = "back_screen_on";

    // ids de boton
    private static final int ID_BIND = 1;
    private static final int ID_KC1 = 2;
    private static final int ID_KC2 = 3;
    private static final int ID_INFO = 4;
    private static final int ID_READ = 5;
    private static final int ID_UNBIND = 6;
    private static final int ID_BC_ON = 7;
    private static final int ID_BC_OFF = 8;
    private static final int ID_NAT_STATE = 9;
    private static final int ID_NAT_HELLO = 10;
    private static final int ID_NAT_BARS = 11;

    private HxyClient mService;
    private boolean mBound;
    private TextView mLog;
    private String mBeforeState = "?";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        log("App lista. Pulsa 'Bind' para conectar al servicio del panel.");
        log("Estado inicial back_screen_on = " + readBackScreen());
        runIntentTest(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        runIntentTest(intent);
    }

    /** Permite disparar pruebas por ADB sin tocar la pantalla:
     *  am start -n com.example.subscreenpoke/.MainActivity --es test hello   */
    private void runIntentTest(Intent intent) {
        if (intent == null) return;
        String test = intent.getStringExtra("test");
        if (test == null) return;
        log("--- test por intent: " + test + " ---");
        if ("state".equals(test)) {
            doNativeState();
        } else if ("hello".equals(test)) {
            doNativeState();
            doDraw(true);
        } else if ("bars".equals(test)) {
            doNativeState();
            doDraw(false);
        } else if ("bcon".equals(test)) {
            doBroadcast(true);
        } else if ("bcoff".equals(test)) {
            doBroadcast(false);
        } else if (test.startsWith("noti")) {
            String msg = intent.getStringExtra("msg");
            doNotify(msg == null ? "Hola mundo" : msg);
        } else {
            log("test desconocido: " + test);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Subscreen Poke");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        root.addView(button(ID_BIND, "1. Bind al servicio"));
        root.addView(button(ID_KC1, "2. handleKeycode(134, action 1)"));
        root.addView(button(ID_KC2, "3. handleKeycode(134, action 2)"));
        root.addView(button(ID_INFO, "4. getServiceInfo(back_screen_on)"));
        root.addView(button(ID_READ, "5. Leer estado (Settings)"));
        root.addView(button(ID_UNBIND, "Unbind"));
        root.addView(button(ID_BC_ON, "6. Broadcast GC_SCREEN_ON (enciende)"));
        root.addView(button(ID_BC_OFF, "7. Broadcast GC_SCREEN_OFF (apaga)"));
        root.addView(button(ID_NAT_STATE, "8. Nativa: estado de la libreria"));
        root.addView(button(ID_NAT_HELLO, "9. Nativa: DIBUJAR Hola mundo"));
        root.addView(button(ID_NAT_BARS, "10. Nativa: DIBUJAR barras de color"));

        mLog = new TextView(this);
        mLog.setTextIsSelectable(true);
        mLog.setPadding(0, dp(12), 0, 0);
        mLog.setTextColor(Color.DKGRAY);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(mLog);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, lp);
        return root;
    }

    private Button button(int id, String text) {
        Button b = new Button(this);
        b.setId(id);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(this);
        return b;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case ID_BIND: doBind(); break;
            case ID_KC1: doKeycode(1); break;
            case ID_KC2: doKeycode(2); break;
            case ID_INFO: doGetInfo(); break;
            case ID_READ: log("back_screen_on = " + readBackScreen()); break;
            case ID_UNBIND: doUnbind(); break;
            case ID_BC_ON: doBroadcast(true); break;
            case ID_BC_OFF: doBroadcast(false); break;
            case ID_NAT_STATE: doNativeState(); break;
            case ID_NAT_HELLO: doDraw(true); break;
            case ID_NAT_BARS: doDraw(false); break;
            default: break;
        }
    }

    // --- ServiceConnection ---

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        mService = new HxyClient(binder);
        mBound = true;
        log("CONECTADO a " + name.flattenToShortString());
        log("binder = " + binder + "  (" + describeBinder(binder) + ")");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mBound = false;
        log("DESCONECTADO de " + name.flattenToShortString());
    }

    // --- Runnable: relectura diferida tras un keycode ---

    @Override
    public void run() {
        String after = readBackScreen();
        String mark = ("1".equals(after) && !"1".equals(mBeforeState)) ? "   << CAMBIO!" : "";
        log("back_screen_on: " + mBeforeState + " -> " + after + mark);
    }

    // --- acciones ---

    private void doBind() {
        if (mBound) { log("Ya estaba bindeado."); return; }
        Intent intent = new Intent(ACTION_SERVICE);
        intent.setPackage(PACKAGE_SERVICE);
        boolean ok = bindService(intent, this, Context.BIND_AUTO_CREATE);
        log("bindService(" + ACTION_SERVICE + ") -> " + ok
                + (ok ? "" : "  (servicio ausente o no exportado?)"));
    }

    private void doUnbind() {
        if (!mBound) { log("No estaba bindeado."); return; }
        try { unbindService(this); } catch (Exception e) { log("unbind err: " + e); }
        mBound = false; mService = null;
        log("unbind hecho.");
    }

    private void doKeycode(int action) {
        if (mService == null) { log("No hay servicio. Pulsa Bind primero."); return; }
        mBeforeState = readBackScreen();
        try {
            boolean handled = mService.handleKeycode(KEYCODE_MINISCREEN_ON, action);
            log("handleKeycode(134, " + action + ") -> handled=" + handled);
        } catch (Throwable t) {
            log("handleKeycode ERROR: " + t);
            return;
        }
        mLog.postDelayed(this, 600);
    }

    private void doGetInfo() {
        if (mService == null) { log("No hay servicio. Pulsa Bind primero."); return; }
        try {
            String v = mService.getServiceInfo(BACK_SCREEN_KEY);
            log("getServiceInfo(back_screen_on) = " + v);
        } catch (Throwable t) {
            log("getServiceInfo ERROR: " + t);
        }
    }

    // --- broadcast: la palanca real de encendido ---

    private void doBroadcast(boolean on) {
        String action = on
                ? "com.yft.miniscreen.action.GC_SCREEN_ON"
                : "com.yft.miniscreen.action.GC_SCREEN_OFF";
        try {
            sendBroadcast(new Intent(action));
            log("sendBroadcast(" + action + ") enviado");
        } catch (Throwable t) {
            log("broadcast ERROR: " + t);
            return;
        }
        mBeforeState = readBackScreen();
        mLog.postDelayed(this, 800);
    }

    // --- libreria nativa del vendor ---

    private void doNativeState() {
        if (BackScreenNative.LOAD_ERROR != null) {
            log("libBackScreen2.so NO cargo: " + BackScreenNative.LOAD_ERROR);
            return;
        }
        log("libBackScreen2.so CARGADA");
        try {
            boolean on = BackScreenNative.native_isScreenOn();
            log("native_isScreenOn() = " + on);
        } catch (Throwable t) {
            log("native_isScreenOn ERROR: " + t);
        }
    }

    private void doDraw(boolean hello) {
        if (BackScreenNative.LOAD_ERROR != null) {
            log("La libreria no cargo; no se puede dibujar.");
            return;
        }
        try {
            BackScreenNative.native_screenOn();
        } catch (Throwable t) {
            log("native_screenOn ERROR: " + t);
        }
        Bitmap bmp = hello
                ? Frames.helloWorld("Hola mundo", "desde mi app")
                : Frames.colorBars();
        log("bitmap " + bmp.getWidth() + "x" + bmp.getHeight()
                + " " + bmp.getConfig() + "  bytes=" + bmp.getByteCount());
        try {
            int ret = BackScreenNative.native_refreshScreen(
                    bmp, BackScreenNative.SCREENCONTENT_MODULE_LENGTH);
            log("native_refreshScreen() = " + ret
                    + (ret == BackScreenNative.SCREENCONTENT_MODULE_LENGTH
                       ? "   << FRAME ESCRITO!" : "   (esperaba "
                         + BackScreenNative.SCREENCONTENT_MODULE_LENGTH + ")"));
        } catch (Throwable t) {
            log("native_refreshScreen ERROR: " + t);
        }
    }

    // --- notificacion: unica via sin root para poner TEXTO PROPIO en el panel ---

    private static final String CHANNEL_ID = "subscreen_poke";

    private void doNotify(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Subscreen Poke", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);

            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Subscreen Poke")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build();
            nm.notify((int) (System.currentTimeMillis() & 0xffff), n);
            log("notificacion posteada: \"" + text + "\"");
        } catch (Throwable t) {
            log("doNotify ERROR: " + t);
        }
    }

    private String readBackScreen() {
        try {
            String v = Settings.Global.getString(getContentResolver(), BACK_SCREEN_KEY);
            return v == null ? "null" : v;
        } catch (Throwable t) {
            return "err(" + t.getClass().getSimpleName() + ")";
        }
    }

    private String describeBinder(IBinder b) {
        try {
            return "descriptor=" + b.getInterfaceDescriptor();
        } catch (Throwable t) {
            return "descriptor=?";
        }
    }

    private static final String TAG = "SubscreenPoke";

    private void log(String s) {
        android.util.Log.i(TAG, s);
        if (mLog == null) return;
        mLog.append(s + "\n");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            try { unbindService(this); } catch (Exception ignored) {}
        }
    }
}
