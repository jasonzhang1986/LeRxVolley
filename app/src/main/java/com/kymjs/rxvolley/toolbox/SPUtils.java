package com.kymjs.rxvolley.toolbox;


import android.content.Context;
import android.content.SharedPreferences;

/**
 * Author: Jifeng Zhang
 * Email : jifengzhang.barlow@gmail.com
 * Date  : 2017/5/8
 * Desc  :
 */

public class SPUtils {
    public static final String KEY_STETHO = "stetho";
    public static final String KEY_OKHTTP = "okhttp";
    private static SharedPreferences sharedPreferences;
    public static void init(Context context) {
        if (sharedPreferences == null) {
            synchronized (SPUtils.class) {
                if (sharedPreferences==null) {
                    sharedPreferences = context.getApplicationContext().getSharedPreferences("RxVolley",Context.MODE_PRIVATE);
                }
            }
        }
    }
    private static SharedPreferences get() {
        if (sharedPreferences == null) {
            throw new IllegalStateException("must be call the init method first");
        }
        return sharedPreferences;
    }
    public static void putBoolean(String key, boolean value) {
        get().edit().putBoolean(key, value).apply();
    }
    public static boolean getBoolean(String key) {
        return get().getBoolean(key, false);
    }
}
