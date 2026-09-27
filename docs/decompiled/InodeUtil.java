package com.hxy.miniscreen.util;

import com.hxy.miniscreen.frame.LogUtil;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/* JADX INFO: loaded from: classes13.dex */
public class InodeUtil {
    public static void update(String path, String value) {
        if (path == null) {
            return;
        }
        File file = new File(path);
        try {
            if (!file.exists()) {
                LogUtil.d(" path:" + path + " not found ");
                return;
            }
            LogUtil.v("update node : " + path + " to " + value);
            FileWriter writer = new FileWriter(file);
            writer.write(value);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String get(String path, String defualtValue) {
        char[] buffer = new char[2];
        FileReader file = null;
        try {
            file = new FileReader(path);
            int len = file.read(buffer, 0, 2);
            defualtValue = new String(buffer, 0, len);
            LogUtil.d("get inode:" + path + " = " + defualtValue);
            file.close();
        } catch (Exception e) {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException io) {
                    LogUtil.d("get path:" + path + " failed ");
                    LogUtil.e(io);
                }
            }
        }
        return defualtValue;
    }
}
