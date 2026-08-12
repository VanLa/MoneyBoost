package com.dream.laughtale;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Native 关闭钮尺寸，SP 缓存 + Remote Config。
 * 统一单一尺寸，不区分 Meta / 非 Meta。
 */
public class LaughTaleLayoutSize {

    private static final String PREFS_NAME = "laughtale_layout";
    private static final String KEY_CLOSE_SIZE = "closeSize_ns";

    public static final float DEFAULT_CLOSE_SIZE_DP = 25f;

    public float closeSize;

    private final SharedPreferences prefs;

    public LaughTaleLayoutSize(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        closeSize = prefs.getFloat(KEY_CLOSE_SIZE, DEFAULT_CLOSE_SIZE_DP);
    }

    public void updateFromRemote(float closeSizeDp) {
        closeSize = closeSizeDp;
        prefs.edit()
                .putFloat(KEY_CLOSE_SIZE, closeSizeDp)
                .apply();
    }
}
