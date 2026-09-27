package com.android.hxyservice.function.miniscreen;

import android.content.Context;
import android.os.Message;
import android.os.SystemProperties;
import android.text.TextUtils;
import com.android.hxyservice.frame.BaseFunction;
import com.android.hxyservice.frame.IServiceUI;

/* JADX INFO: loaded from: classes8.dex */
public class MiniscreenFunction extends BaseFunction {
    private static final String BACK_SREEN_KEY = "back_screen_on";

    public MiniscreenFunction(Context context, IServiceUI mUi) {
        super(context, mUi);
    }

    @Override // com.android.hxyservice.frame.BaseFunction
    public boolean isSupported() {
        return !TextUtils.isEmpty(SystemProperties.get("ro.hxy_support_tinylcd", ""));
    }

    @Override // com.android.hxyservice.frame.BaseFunction
    public String tag() {
        return getClass().getSimpleName();
    }

    @Override // com.android.hxyservice.frame.BaseFunction
    public void init() {
        requestKeycode(134, 1);
        requestKeycode(134, 2);
    }

    @Override // com.android.hxyservice.frame.BaseFunction
    public void onKeycode(int keycode, int type) {
        if (getGlobalValue(BACK_SREEN_KEY, 0) == 0) {
            putGlobalValue(BACK_SREEN_KEY, 1);
        }
    }

    @Override // com.android.hxyservice.frame.BaseFunction
    protected boolean onMessage(Message message) {
        return false;
    }
}
