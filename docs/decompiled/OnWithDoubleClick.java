package com.hxy.miniscreen.tinylcd;

import android.content.Context;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import com.hxy.miniscreen.R;
import com.hxy.miniscreen.camera.CameraUtil;
import com.hxy.miniscreen.frame.BaseFunction;
import com.hxy.miniscreen.frame.IServiceUI;
import com.hxy.miniscreen.util.InodeUtil;

/* JADX INFO: loaded from: classes13.dex */
public class OnWithDoubleClick extends BaseFunction {
    private static final String SETTINGS_DOUBLE_CLICK = "double_touch_key";
    private final String BACK_SREEN_KEY;
    private String mGestureNode;
    private String mGestureNodeThree;
    private boolean mIsOn;
    private boolean mIsOtherOn;

    public OnWithDoubleClick(Context context, IServiceUI mUi) {
        super(context, mUi);
        this.BACK_SREEN_KEY = "back_screen_on";
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public boolean isSupported() {
        return getBoolean(R.bool.config_double_click) && !TextUtils.isEmpty(getStr(R.string.default_double_click_inode));
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public String tag() {
        return getClass().getSimpleName();
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public void init() {
        this.mGestureNode = getStr(R.string.default_double_click_inode);
        this.mGestureNodeThree = getStr(R.string.default_double_click_inode_three);
        this.mIsOn = addSettingListener(SETTINGS_DOUBLE_CLICK, getInt(R.integer.default_double_click)) == 1;
        InodeUtil.update(this.mGestureNode, this.mIsOn ? "1" : CameraUtil.CAMERA_BACK);
        InodeUtil.update(this.mGestureNodeThree, this.mIsOn ? "1" : CameraUtil.CAMERA_BACK);
        addSettingListener("back_screen_on", getInt(R.integer.default_double_click));
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    protected boolean onMessage(Message message) {
        switch (message.what) {
            case 106:
                Logv("screen on update key to 1");
                if (Settings.Global.getInt(this.mContext.getContentResolver(), "back_screen_on", 0) == 0) {
                    this.mIsOtherOn = true;
                    Settings.Global.putInt(this.mContext.getContentResolver(), "back_screen_on", 1);
                }
                break;
            case 107:
                this.mIsOtherOn = false;
                if (Settings.Global.getInt(this.mContext.getContentResolver(), "back_screen_on", 0) == 1) {
                    Logv("screen off update key to 1");
                    Settings.Global.putInt(this.mContext.getContentResolver(), "back_screen_on", 0);
                }
                break;
        }
        return false;
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public void onSettingChange(String key, int i) {
        if (TextUtils.equals(SETTINGS_DOUBLE_CLICK, key)) {
            this.mIsOn = i == 1;
            this.mUi.sendRequest(106);
            this.mUi.getMainHandler().postDelayed(new Runnable() { // from class: com.hxy.miniscreen.tinylcd.OnWithDoubleClick$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onSettingChange$0();
                }
            }, 100L);
            this.mUi.getMainHandler().postDelayed(new Runnable() { // from class: com.hxy.miniscreen.tinylcd.OnWithDoubleClick$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onSettingChange$1();
                }
            }, 100L);
            Logv(" turn double touch on = " + this.mIsOn);
        } else if (this.mIsOn && TextUtils.equals("back_screen_on", key) && Settings.Global.getInt(this.mContext.getContentResolver(), "back_screen_on", 0) == 1 && !this.mIsOtherOn) {
            Logv("double touch and setting is on, turn on scren ");
            this.mUi.sendRequest(106);
        }
        Logd("key = " + key + " value = " + i + " mIsOn = " + this.mIsOn + " turn on = " + (Settings.Global.getInt(this.mContext.getContentResolver(), "back_screen_on", 0) == 1));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onSettingChange$0() {
        InodeUtil.update(this.mGestureNode, this.mIsOn ? "1" : CameraUtil.CAMERA_BACK);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onSettingChange$1() {
        InodeUtil.update(this.mGestureNodeThree, this.mIsOn ? "1" : CameraUtil.CAMERA_BACK);
    }
}
