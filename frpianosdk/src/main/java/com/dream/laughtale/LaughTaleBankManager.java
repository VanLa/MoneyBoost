package com.dream.laughtale;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Random;

public class LaughTaleBankManager {

    private Context mContext;
    public static LaughTaleBankManager instance;
    private SharedPreferences preferences;
    private SharedPreferences userDayPreferences;

    public static LaughTaleBankManager instance() {
        if (instance == null) {
            synchronized (LaughTaleBankManager.class) {
                if (instance == null) {
                    instance = new LaughTaleBankManager();
                }
            }
        }
        return instance;
    }

    private static final String PREFS_NAME = "AdStatsPrefs";
    private static final String LAST_DAY_KEY = "LastDay";
    private static final String INTERSTITIAL_ECPM_KEY = "InterstitialEcpm";
    private static final String NATIVE_ECPM_KEY = "NativeEcpm";
    private static final String INTERSTITIAL_COUNT_KEY = "InterstitialCount";
    private static final String NATIVE_COUNT_KEY = "NativeCount";

    private static final String USER_DAY_PREFS_NAME = "UserDayPreferences";
    private static final String LAST_OPEN_DATE_KEY = "LastOpenDate";
    private static final String USER_DAY_COUNT_KEY = "UserDayCount";

    private static final String ADJUST_CONFIG_PREFS_NAME = "AdjustConfigPrefs";
    private static final String THRESHOLD_KEY = "Threshold";
    private static final String ADJUST_PERCENTAGE_KEY = "AdjustPercentage";
    private static final String LOW_TARGET_KEY = "LowTarget";
    private static final String MIN_TARGET_KEY = "MinTarget";
    private static final String SHOW_RATE_KEY = "ShowRate";

    private static final double THRESHOLD_ECPM = 1.0; // Default threshold for adjusting the template

    private int LaughTale_userDayCount = 0;
    private float mShowRate = 1.0f;
    private float mThreshold = 0.2f;
    private float mAdjustPercentage = 0.8f;
    private float mLowTarget = 0.5f;
    private float mMinTarget = 0.2f;

    // 获取当前日期
    private int getCurrentDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.DAY_OF_YEAR);
    }

    // 初始化广告管理器
    public void LaughTaleInitBankManager(Context context) {
        mContext = context;
        preferences = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        userDayPreferences = mContext.getSharedPreferences(USER_DAY_PREFS_NAME, Context.MODE_PRIVATE);

        // Load adjustConfig from local cache
        loadAdjustConfig();
        // Load mShowRate from local cache
        loadShowRate();
        getUserDay();
    }

    // 从本地缓存加载 adjustConfig 参数
    private void loadAdjustConfig() {
        SharedPreferences adjustConfigPreferences = mContext.getSharedPreferences(ADJUST_CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        mThreshold = adjustConfigPreferences.getFloat(THRESHOLD_KEY, 0.2f);
        mAdjustPercentage = adjustConfigPreferences.getFloat(ADJUST_PERCENTAGE_KEY, 0.8f);
        mLowTarget = adjustConfigPreferences.getFloat(LOW_TARGET_KEY, 0.5f);
        mMinTarget = adjustConfigPreferences.getFloat(MIN_TARGET_KEY, 0.2f);
    }

    // 从本地缓存加载 mShowRate 参数
    private void loadShowRate() {
        SharedPreferences adjustConfigPreferences = mContext.getSharedPreferences(ADJUST_CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        mShowRate = adjustConfigPreferences.getFloat(SHOW_RATE_KEY, 1.0f);
    }

    // 根据浮动阈值和调整百分比来动态调整展示率
    public void adjustConfig(float threshold, float adjustPercentage, float lowTarget , float minTarget) {
        mThreshold = threshold;
        mAdjustPercentage = adjustPercentage;
        mLowTarget = lowTarget;
        mMinTarget = minTarget;

        // Save to local cache
        SharedPreferences.Editor editor = mContext.getSharedPreferences(ADJUST_CONFIG_PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putFloat(THRESHOLD_KEY, mThreshold);
        editor.putFloat(ADJUST_PERCENTAGE_KEY, mAdjustPercentage);
        editor.putFloat(LOW_TARGET_KEY, mLowTarget);
        editor.putFloat(MIN_TARGET_KEY, mMinTarget);
        editor.apply();
    }

    // 更新广告统计数据
    public void updateAdStats(String adType, double ecpm) {
        SharedPreferences.Editor editor = preferences.edit();
        String ecpmKey = adType.equals("inter") ? INTERSTITIAL_ECPM_KEY : NATIVE_ECPM_KEY;
        String countKey = adType.equals("inter") ? INTERSTITIAL_COUNT_KEY : NATIVE_COUNT_KEY;

        double totalEcpm = preferences.getFloat(ecpmKey, 0f);
        int count = preferences.getInt(countKey, 0);

        totalEcpm += ecpm;
        count++;

        editor.putFloat(ecpmKey, (float) totalEcpm);  // Storing as float in SharedPreferences
        editor.putInt(countKey, count);
        editor.apply();
    }

    // 获取广告的每日平均eCPM
    public double getAverageEcpm(String adType) {
        String ecpmKey = adType.equals("inter") ? INTERSTITIAL_ECPM_KEY : NATIVE_ECPM_KEY;
        String countKey = adType.equals("inter") ? INTERSTITIAL_COUNT_KEY : NATIVE_COUNT_KEY;

        double totalEcpm = preferences.getFloat(ecpmKey, 0f);
        int count = preferences.getInt(countKey, 0);

        return count == 0 ? 0d : totalEcpm / count;
    }

    // 判断是否需要调整广告模板
    public boolean shouldAdjustAdTemplate(String adType) {
        double avgEcpm = getAverageEcpm(adType);
        return avgEcpm < THRESHOLD_ECPM;
    }

    public boolean getIsOldUser(){
        if (LaughTale_userDayCount >= 2) {
            return true;
        }else {
            return false;
        }
    }

    // 获取展示决定：needShow 和 modMode，按广告类型进行分开计算
    public boolean[] getShowDecision(String adType, double ecpm) {
        boolean[] result = new boolean[2];
        boolean needShow = true; // 默认值为true
        boolean modMode = false;

        // 打印日志
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "===LaughTaleShowDecision for adType: " + adType + ", eCPM: " + ecpm);

        // 判断 eCPM
        if (adType.equals("inter")) {
            // 如果是插页广告，前两次不调整策略
            if (preferences.getInt(INTERSTITIAL_COUNT_KEY, 0) > 2) {
                needShow = handleShowDecision(ecpm, adType);
            }
        } else if (adType.equals("native")) {
            // 如果是原生广告，前三次不调整策略
            if (preferences.getInt(NATIVE_COUNT_KEY, 0) > 3) {
                needShow = handleShowDecision(ecpm, adType);
            }
        }

        // 根据 mShowRate 判断是否进入 modMode
        if (mShowRate < mLowTarget && LaughTale_userDayCount >= 1) {
            modMode = true;
        }

        result[0] = needShow;
        result[1] = modMode;

        // 打印日志
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "===NeedShow: " + needShow + ", modMode: " + modMode);

        return result;
    }

    // 处理展示逻辑
    private boolean handleShowDecision(double ecpm, String adType) {
        boolean needShow = true; // 默认值为true

        // 打印日志
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "===HandleShowDecision for adType: " + adType + ", eCPM: " + ecpm);

        if (ecpm < (1 - mThreshold)) {
            // 低于下限，减少展示概率
            mShowRate *= mAdjustPercentage;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "===eCPM < (1 - mThreshold), Adjusted mShowRate: " + mShowRate);
            if (new Random().nextFloat() < mShowRate) {
                needShow = true;
            } else {
                needShow = false;
            }
        } else if (ecpm > (1 + mThreshold) && mShowRate < 1) {
            // 高于上限，增加展示概率
            mShowRate /= mAdjustPercentage;
            if (mShowRate > 1) {
                mShowRate = 1.0f;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "===eCPM > (1 + mThreshold), Adjusted mShowRate: " + mShowRate);
            if (new Random().nextFloat() < mShowRate) {
                needShow = true;
            } else {
                needShow = false;
            }
        }

        // 如果展示率小于最小目标，则直接返回true
        if (mShowRate < mMinTarget) {
            needShow = true;
        }

        // 保存 mShowRate 到本地缓存
        SharedPreferences.Editor editor = mContext.getSharedPreferences(ADJUST_CONFIG_PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putFloat(SHOW_RATE_KEY, mShowRate);
        editor.apply();

        return needShow;
    }

    // 获取用户的天数
// 获取用户的天数并判断是否为新的一天
    private void getUserDay() {
        int currentDay = getCurrentDay();
        int savedDay = userDayPreferences.getInt(LAST_OPEN_DATE_KEY, 0);

        if (savedDay != currentDay) {
            // 是新的一天
            // 增加用户天数计数器
            LaughTale_userDayCount = userDayPreferences.getInt(USER_DAY_COUNT_KEY, 0) + 1;

            // 打印日志，显示新的一天
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "=== New day detected. CurrentDay: " + currentDay + ", SavedDay: " + savedDay + ", UserDayCount: " + LaughTale_userDayCount);

            // 保存新的日期和天数
            SharedPreferences.Editor editor = userDayPreferences.edit();
            editor.putInt(LAST_OPEN_DATE_KEY, currentDay);
            editor.putInt(USER_DAY_COUNT_KEY, LaughTale_userDayCount);
            editor.apply();
        } else {
            // 不是新的一天
            LaughTale_userDayCount = userDayPreferences.getInt(USER_DAY_COUNT_KEY, 0);
        }
    }
//
//    // 重置相关本地参数
//    private void resetLocalParams() {
//        SharedPreferences.Editor adStatsEditor = preferences.edit();
//
//        // 重置广告统计相关参数
//        adStatsEditor.putFloat(INTERSTITIAL_ECPM_KEY, 0f);
//        adStatsEditor.putFloat(NATIVE_ECPM_KEY, 0f);
//        adStatsEditor.putInt(INTERSTITIAL_COUNT_KEY, 0);
//        adStatsEditor.putInt(NATIVE_COUNT_KEY, 0);
//        adStatsEditor.apply();
//
//        // 重置展示率
//        SharedPreferences.Editor adjustConfigEditor = mContext.getSharedPreferences(ADJUST_CONFIG_PREFS_NAME, Context.MODE_PRIVATE).edit();
//        adjustConfigEditor.putFloat(SHOW_RATE_KEY, 1.0f); // 重置为默认值
//        adjustConfigEditor.apply();
//
//        // 打印重置日志
//        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleBankManager", "=== Local parameters have been reset for the new day");
//    }

}
