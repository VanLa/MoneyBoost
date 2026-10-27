package com.freddie.frmusicsdk;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;
import com.joyboost.moneyboost.MoneyBoostLocalPush;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Java-only host example. In Unity, ColorLocalPushService supplies these parameters instead. */
final class DemoLocalPush {
    private static final String PREFS = "moneyboost_demo_push";
    private static final int FALLBACK_HOUR = 20;
    private static final int INTERVAL_DAYS = 1;

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String onResume(Activity activity) {
        MoneyBoostLocalPush.setForeground(true);
        // Cancellation remains effective when returning to the demo.
        return state(activity).getBoolean("enabled", false) ? register(activity) : "本地推送未开启：点击下方按钮注册 Day1 推送并授权。";
    }

    static String enable(Activity activity) {
        String status = register(activity);
        if (state(activity).getBoolean("enabled", false)) MoneyBoostLocalPush.requestPermission(activity);
        return status;
    }

    static String cancel(Activity activity) {
        state(activity).edit().putBoolean("enabled", false).apply();
        MoneyBoostLocalPush.cancel(activity);
        return "本地推送已取消；重新打开 Demo 不会自动恢复。";
    }

    private static String register(Activity activity) {
        try {
            JSONObject library;
            try (InputStream input = activity.getAssets().open("local_push_messages.json");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                library = new JSONObject(output.toString("UTF-8"));
            }
            Locale locale = activity.getResources().getConfiguration().getLocales().get(0);
            String language = locale.getLanguage();
            if (!library.has(language)) language = "en";
            JSONArray messages = library.getJSONArray(language);
            Calendar now = Calendar.getInstance();
            Calendar first = (Calendar) now.clone();
            first.add(Calendar.DAY_OF_YEAR, 1); // Day1, never a reminder on the day the user opens.
            if (now.get(Calendar.HOUR_OF_DAY) < 8 || now.get(Calendar.HOUR_OF_DAY) >= 22) {
                first.set(Calendar.HOUR_OF_DAY, FALLBACK_HOUR);
                first.set(Calendar.MINUTE, 0);
            }
            first.set(Calendar.SECOND, 0);
            first.set(Calendar.MILLISECOND, 0);

            // The production Unity host calls this exact API through AndroidJavaClass.
            boolean registered = MoneyBoostLocalPush.register(activity, first.getTimeInMillis(),
                INTERVAL_DAYS, FALLBACK_HOUR, messages.toString(), "ic_launcher");
            if (!registered) return "推送注册失败，详情见 MoneyBoostLocalPush 日志。";
            state(activity).edit().putBoolean("enabled", true).apply();
            String status = "已注册 Day1：" + DateFormat.getDateTimeInstance().format(first.getTime())
                + "\n语言：" + language + " · " + messages.length() + " 组文案轮换 · 每日最多一条"
                + "\n无新打开记录时，后续默认当地 20:00；系统可能延迟送达。"
                + (NotificationManagerCompat.from(activity).areNotificationsEnabled() ? "" : "\n通知尚未授权，请点击注册按钮授权或在系统设置中开启。");
            Log.i("MoneyBoostDemo", status);
            return status;
        } catch (Exception error) {
            Log.e("MoneyBoostDemo", "Local push registration failed", error);
            return "推送注册失败：" + error.getMessage();
        }
    }
}
