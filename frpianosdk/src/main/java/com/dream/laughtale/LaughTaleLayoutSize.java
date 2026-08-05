package com.dream.laughtale;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Native 关闭钮尺寸，对齐竞品 {@code *_sz.json} 默认参数与 SP 缓存。
 * 远程下发见 {@link LaughTaleLayoutSize#updateFromRemote}。
 */
public class LaughTaleLayoutSize {

    private static final String PREFS_NAME = "laughtale_layout";
    private static final String KEY_CLOSE_SIZE = "closeSize_ns";
    private static final String KEY_CLOSE_SIZE_META = "closeSize_ns_0";

    /** 对齐竞品 LayoutSize：非 Meta 20dp，Meta 25dp */
    public static final float DEFAULT_CLOSE_SIZE_DP = 25f;
    public static final float DEFAULT_CLOSE_SIZE_META_DP = 25f;

    public float closeSize;
    public float closeSizeMeta;

    private final SharedPreferences prefs;

    public LaughTaleLayoutSize(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        closeSize = prefs.getFloat(KEY_CLOSE_SIZE, DEFAULT_CLOSE_SIZE_DP);
        closeSizeMeta = prefs.getFloat(KEY_CLOSE_SIZE_META, DEFAULT_CLOSE_SIZE_META_DP);
    }

    public void updateFromRemote(float closeSizeDp, float closeSizeMetaDp) {
        closeSize = closeSizeDp;
        closeSizeMeta = closeSizeMetaDp;
        prefs.edit()
                .putFloat(KEY_CLOSE_SIZE, closeSizeDp)
                .putFloat(KEY_CLOSE_SIZE_META, closeSizeMetaDp)
                .apply();
    }
}
