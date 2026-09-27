package com.hxy.miniscreen.tinylcd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserHandle;
import android.view.View;
import com.freeme.backscreen.BackScreenNative;
import com.hxy.miniscreen.R;
import com.hxy.miniscreen.frame.BaseFunction;
import com.hxy.miniscreen.frame.FeatureOption;
import com.hxy.miniscreen.frame.IServiceUI;
import com.hxy.miniscreen.util.BitmapUtil;
import com.hxy.miniscreen.util.PhotoUtils;

/* JADX INFO: loaded from: classes13.dex */
public class TinylcdFunction extends BaseFunction {
    private static final int FPS = 30;
    private static final String GC_SCREEN_OFF = "com.yft.miniscreen.action.GC_SCREEN_OFF";
    private static final String GC_SCREEN_ON = "com.yft.miniscreen.action.GC_SCREEN_ON";
    private static final int MSG_FRESH_FRAME = 1;
    private static final int MSG_FRESH_FRAME_ONE = 2;
    private static final int MSG_REAL_FRESH_FRAME = 1;
    private static final int MSG_REAL_SET_LUX_LEVEL = 4;
    private static final int MSG_REAL_TRUN_OFF = 3;
    private static final int MSG_REAL_TRUN_ON = 2;
    private static final int MSG_SCREEN_AFTER_OFF = 5;
    private static int POWER_SAVER_FPS = 8;
    private boolean isSkipFrame;
    private int mCurrentLevel;
    private int mFlushCount;
    private int mFps;
    private HandlerThread mHandlerThread;
    private boolean mIsBitmapUsed;
    private boolean mIsScreenOn;
    private boolean mIsScreenPreOff;
    private long mLastCountTime;
    private long mLastCreateTime;
    private Handler mMainHandler;
    private Matrix mMatrix;
    private PowerManager mPowerManager;
    private Handler mRefreshHandler;
    private Bitmap mShowBitmap;
    private View mShowView;
    private int mTempLuxLevel;
    private PowerManager.WakeLock mWakeLock;
    private int outHeight;
    private int outWidth;
    private int showHeight;
    private int showWidth;

    public TinylcdFunction(Context context, IServiceUI mUi) {
        super(context, mUi);
        this.mFlushCount = -1;
        this.showWidth = FeatureOption.MINISCREEN_WIDTH;
        this.showHeight = FeatureOption.MINISCREEN_HEIGHT;
        this.outWidth = FeatureOption.MINISCREEN_OUT_WIDTH;
        this.outHeight = FeatureOption.MINISCREEN_OUT_HEIGHT;
        this.mIsBitmapUsed = true;
        this.mIsScreenOn = true;
        this.mIsScreenPreOff = false;
        this.isSkipFrame = false;
    }

    public void setShowView(View view) {
        this.mShowView = view;
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public boolean isSupported() {
        return true;
    }

    public boolean getScreenOnStatus() {
        return this.mIsScreenOn;
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public void init() {
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mWakeLock = this.mPowerManager.newWakeLock(1, "miniscreen_waklock");
        this.mWakeLock.setReferenceCounted(false);
        POWER_SAVER_FPS = this.mContext.getResources().getInteger(R.integer.config_power_saver_fps);
        this.mFps = 30;
        this.mMainHandler = new MainHandler();
        this.mHandlerThread = new HandlerThread("miniscreen_flush");
        this.mHandlerThread.start();
        this.mRefreshHandler = new FlushHandler(this.mHandlerThread.getLooper());
        tryAddFunction(new ScheduleOffFunction(this.mContext, this.mUi));
        tryAddFunction(new ScheduleHomeFunction(this.mContext, this.mUi));
        tryAddFunction(new OnWithScreenFunction(this.mContext, this.mUi));
        tryAddFunction(new OnWithNotification(this.mContext, this.mUi));
        tryAddFunction(new OnWithDoubleClick(this.mContext, this.mUi));
        tryAddFunction(new LuxLevelFunction(this.mContext, this.mUi));
        tryAddFunction(new FlipTurnOnFunction(this.mContext, this.mUi));
        this.mMatrix = new Matrix();
        this.mMatrix.postScale((this.outWidth * 1.0f) / this.showWidth, (this.outHeight * 1.0f) / this.showHeight);
        startFlush();
        this.mWakeLock.acquire();
        initReceiver();
    }

    private void initReceiver() {
        Logd("register receiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(GC_SCREEN_ON);
        filter.addAction(GC_SCREEN_OFF);
        filter.setPriority(Integer.MAX_VALUE);
        this.mContext.registerReceiverAsUser(new BroadcastReceiver() { // from class: com.hxy.miniscreen.tinylcd.TinylcdFunction.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                TinylcdFunction.this.Logd(" receive message = " + intent.getAction());
                intent.getExtras();
                if (TinylcdFunction.GC_SCREEN_ON.equals(intent.getAction())) {
                    TinylcdFunction.this.mUi.sendRequest(106);
                } else if (TinylcdFunction.GC_SCREEN_OFF.equals(intent.getAction())) {
                    TinylcdFunction.this.mUi.sendRequest(107);
                }
            }
        }, UserHandle.ALL, filter, null, null, 2);
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public String tag() {
        return getClass().getSimpleName();
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    protected boolean onMessage(Message message) {
        switch (message.what) {
            case 105:
                this.mMainHandler.sendEmptyMessageDelayed(2, 1000L);
                return false;
            case 106:
                this.mIsScreenOn = true;
                this.mIsScreenPreOff = false;
                this.mRefreshHandler.removeMessages(5);
                this.mWakeLock.acquire();
                startFlush();
                this.mRefreshHandler.sendEmptyMessage(2);
                return true;
            case 107:
                this.mIsScreenPreOff = true;
                if (this.mWakeLock.isHeld()) {
                    this.mWakeLock.release();
                }
                this.mRefreshHandler.sendEmptyMessage(3);
                return true;
            case 108:
                Message msg = this.mRefreshHandler.obtainMessage(4);
                msg.arg1 = message.arg1;
                this.mRefreshHandler.sendMessage(msg);
                return true;
            case 117:
                this.mFps = 30;
                getFps();
                return false;
            case 118:
                this.mFps = POWER_SAVER_FPS;
                getFps();
                return false;
            case 119:
                boolean tempMax = message.arg1 == 1;
                if (tempMax) {
                    this.mTempLuxLevel = this.mCurrentLevel;
                }
                this.mUi.sendRequest(108, tempMax ? 2 : this.mTempLuxLevel);
                return false;
            case 121:
                this.mUi.sendRequest(107);
                return true;
            case IServiceUI.MSG_CLOSE /* 130 */:
                this.mUi.sendRequest(107);
                this.mMainHandler.postDelayed(new Runnable() { // from class: com.hxy.miniscreen.tinylcd.TinylcdFunction$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        Process.killProcess(Process.myPid());
                    }
                }, 200L);
                return true;
            default:
                return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startFlush() {
        if (this.mIsScreenOn) {
            this.mMainHandler.sendEmptyMessageDelayed(1, 1000 / this.mFps);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopFlush() {
        this.mMainHandler.removeMessages(1);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateBitmap() {
        this.mMainHandler.postDelayed(new Runnable() { // from class: com.hxy.miniscreen.tinylcd.TinylcdFunction$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$updateBitmap$1();
            }
        }, 500 / this.mFps);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$updateBitmap$1() {
        if (this.mShowView != null) {
            long beforeFlush = System.currentTimeMillis();
            if (this.mIsBitmapUsed || beforeFlush - this.mLastCreateTime > 200) {
                Bitmap tempBitmap = BitmapUtil.loadBitmapFromView(this.mShowView, FeatureOption.MINISCREEN_WIDTH, FeatureOption.MINISCREEN_HEIGHT);
                if (this.showWidth != this.outWidth || this.showHeight != this.outHeight) {
                    tempBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, this.showWidth, this.showHeight, this.mMatrix, true);
                }
                this.mShowBitmap = PhotoUtils.Rgb565Bitmap(tempBitmap);
                this.mLastCreateTime = System.currentTimeMillis();
                this.mIsBitmapUsed = false;
                Logt("new bitmap cost = " + (this.mLastCreateTime - beforeFlush));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void writeFrame() {
        if (this.mShowBitmap != null && this.mShowBitmap.getWidth() == FeatureOption.MINISCREEN_OUT_WIDTH) {
            dumpInput(this.mShowBitmap);
            long beforeFlush = System.currentTimeMillis();
            BackScreenNative.native_refreshScreen(this.mShowBitmap, BackScreenNative.SCREENCONTENT_MODULE_LENGTH);
            if (!this.isSkipFrame) {
                this.isSkipFrame = true;
                Message msg = this.mRefreshHandler.obtainMessage(4);
                msg.arg1 = this.mCurrentLevel;
                this.mRefreshHandler.sendMessageDelayed(msg, 500L);
            }
            this.mIsBitmapUsed = true;
            long afterFlush = System.currentTimeMillis();
            Logt("flush frame cost = " + (afterFlush - beforeFlush));
            if (this.mFlushCount > 100) {
                getFps();
            } else if (this.mFlushCount == 0) {
                this.mLastCountTime = afterFlush;
            }
            this.mFlushCount++;
        }
    }

    private float getFps() {
        long currentTime = System.currentTimeMillis();
        float fps = (this.mFlushCount * 1000.0f) / (currentTime - this.mLastCountTime);
        Logv("flush frame fps =  " + fps);
        this.mFlushCount = -1;
        return fps;
    }

    private void dumpInput(Bitmap bitmap) {
        if (FeatureOption.IS_TEST) {
            String title = System.currentTimeMillis() + "";
            PhotoUtils.saveImage(this.mContext, title + "_dump.jpg", bitmap);
        }
    }

    private boolean isScreenOn() {
        boolean isScreenOn = BackScreenNative.native_isScreenOn();
        Logt(" isScrenOn = " + isScreenOn);
        return isScreenOn;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void turnScreenOn() {
        if (!BackScreenNative.native_isScreenOn()) {
            Logv("turnScreenOn");
            this.mRefreshHandler.postDelayed(new Runnable() { // from class: com.hxy.miniscreen.tinylcd.TinylcdFunction$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$turnScreenOn$2();
                }
            }, 100L);
            BackScreenNative.native_screenOn();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$turnScreenOn$2() {
        setLuxLevel(this.mCurrentLevel);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void turnScreenOff() {
        if (BackScreenNative.native_isScreenOn()) {
            Logv("turnScreenOff");
            BackScreenNative.native_screenOff();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setLuxLevel(int level) {
        int level2;
        int level3;
        Logv("set lux level = " + level + ", isSkipFrame = " + this.isSkipFrame);
        if (level > 8) {
            level = 8;
        }
        if (BackScreenNative.native_isScreenOn() && this.isSkipFrame) {
            if (level < 6) {
                level2 = (level - 1) * 2;
            } else {
                level2 = ((level - 1) * 2) + 1;
            }
            if (level2 < 0) {
                level3 = 0;
            } else if (level2 <= 15) {
                level3 = level2;
            } else {
                level3 = 15;
            }
            Logv("set real lux level = " + level3);
            BackScreenNative.native_setBrightnessScreen(level3);
        }
    }

    private class MainHandler extends Handler {
        public MainHandler() {
            super(Looper.getMainLooper());
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                case 2:
                    removeMessages(1);
                    TinylcdFunction.this.startFlush();
                    TinylcdFunction.this.updateBitmap();
                    if (msg.what == 1) {
                        TinylcdFunction.this.mRefreshHandler.sendEmptyMessage(1);
                    }
                    break;
            }
        }
    }

    private class FlushHandler extends Handler {
        public FlushHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    removeMessages(1);
                    TinylcdFunction.this.writeFrame();
                    break;
                case 2:
                    TinylcdFunction.this.turnScreenOn();
                    break;
                case 3:
                    TinylcdFunction.this.turnScreenOff();
                    sendEmptyMessageDelayed(5, 1000L);
                    break;
                case 4:
                    TinylcdFunction.this.mCurrentLevel = msg.arg1;
                    TinylcdFunction.this.setLuxLevel(TinylcdFunction.this.mCurrentLevel);
                    break;
                case 5:
                    if (TinylcdFunction.this.mIsScreenPreOff) {
                        TinylcdFunction.this.mIsScreenOn = false;
                        TinylcdFunction.this.stopFlush();
                    }
                    TinylcdFunction.this.mIsScreenPreOff = false;
                    break;
            }
        }
    }
}
