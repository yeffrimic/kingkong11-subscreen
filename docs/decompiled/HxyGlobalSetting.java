package com.hxy.miniscreen.frame;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

/* JADX INFO: loaded from: classes13.dex */
public abstract class HxyGlobalSetting extends ContentObserver {
    private final Context mContext;
    private final int mDefaultValue;
    private final String mSettingName;

    public abstract void handleValueChanged(int i);

    public HxyGlobalSetting(Context context, Handler handler, String str) {
        super(handler);
        this.mContext = context;
        this.mSettingName = str;
        this.mDefaultValue = 0;
    }

    public HxyGlobalSetting(Context context, Handler handler, String str, int value) {
        super(handler);
        this.mContext = context;
        this.mSettingName = str;
        this.mDefaultValue = value;
    }

    public String getKey() {
        return this.mSettingName;
    }

    public int getValue() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), this.mSettingName, this.mDefaultValue);
    }

    public void setValue(int i) {
        Settings.Global.putInt(this.mContext.getContentResolver(), this.mSettingName, i);
    }

    public void setListening(boolean regist) {
        if (regist) {
            this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(this.mSettingName), false, this);
        } else {
            this.mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    @Override // android.database.ContentObserver
    public void onChange(boolean selfChange) {
        handleValueChanged(getValue());
    }
}
