package com.hxy.miniscreen.tinylcd;

import android.content.Context;
import android.os.Message;
import com.hxy.miniscreen.R;
import com.hxy.miniscreen.frame.BaseFunction;
import com.hxy.miniscreen.frame.IServiceUI;

/* JADX INFO: loaded from: classes13.dex */
public class OnWithNotification extends BaseFunction {
    private boolean mIsOn;

    public OnWithNotification(Context context, IServiceUI mUi) {
        super(context, mUi);
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public boolean isSupported() {
        return getBoolean(R.bool.config_notification_on);
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public String tag() {
        return getClass().getSimpleName();
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public void init() {
        this.mIsOn = addSettingListener("subscreen_noti_screenon", getInt(R.integer.default_notification_on)) == 1;
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    protected boolean onMessage(Message message) {
        switch (message.what) {
            case 102:
                if (this.mIsOn && message.arg1 == 1) {
                    Logv(" has notification and notification on, turn on screen ");
                    this.mUi.sendRequest(106);
                }
                break;
        }
        return false;
    }

    @Override // com.hxy.miniscreen.frame.BaseFunction
    public void onSettingChange(String key, int i) {
        this.mIsOn = i == 1;
        Logv(" turn notification on = " + this.mIsOn);
    }
}
