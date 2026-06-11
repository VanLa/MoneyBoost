package com.freddie.frmusicsdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class NotificationHelper {
    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_LAST_CLICK_DATE = "last_click_date";
    private static final String KEY_LAST_SEND_TIME = "last_send_time";

    public static void sendCustomNotification(Context context, java.util.List<String> textOptions) {
        try {
            if (shouldSkipNotification(context)) return;

            // 动态生成ID和组名
            long timestamp = System.currentTimeMillis();
            String channelId = "channel_" + timestamp + "_" + generateFourDigitId();
            String groupId = "group_" + generateFourDigitId() + "_" + (timestamp % 1000);
            int notificationId = generateFourDigitId();

            String randomText = textOptions.get(new Random().nextInt(textOptions.size()));
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.sprunki_beats_custom_notification);
            remoteViews.setTextViewText(R.id.tv_message, randomText);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, createChannel(context, channelId))
                    .setSmallIcon(R.drawable.transparent_icon)
                    .setCustomContentView(remoteViews)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(getContentIntent(context))
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                    .setGroup(groupId);

            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            recordSendTime(context);
        } catch (Exception e) {
            Log.e("Notification", "Failed: " + Log.getStackTraceString(e));
        }
    }

    private static int generateFourDigitId() {
        return new Random().nextInt(9000) + 1000; // 1000-9999
    }

    private static boolean shouldSkipNotification(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 检查当天是否点击过
        int lastClickDay = prefs.getInt(KEY_LAST_CLICK_DATE, -1);
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        if (lastClickDay == currentDay) return true;

        // 检查冷却时间
        long lastSendTime = prefs.getLong(KEY_LAST_SEND_TIME, 0L);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastSendTime) < 180000; // 3分钟
    }

    private static void recordSendTime(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(KEY_LAST_SEND_TIME, System.currentTimeMillis());
        editor.apply();
    }

    public static void recordClickDate(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt(KEY_LAST_CLICK_DATE, Calendar.getInstance().get(Calendar.DAY_OF_YEAR));
        editor.apply();
    }

    private static String createChannel(Context context, String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "default";

        // 动态渠道名称
        String channelName = "DesktopNotify_" + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        NotificationChannel channel = new NotificationChannel(channelId, channelName,
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Shows when returning to desktop");
        channel.enableLights(true);
        channel.setLightColor(Color.YELLOW);
        channel.setShowBadge(true);
        channel.enableVibration(true);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
        return channelId;
    }

    private static PendingIntent getContentIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("NOTIFICATION_CLICK", true);

        return PendingIntent.getActivity(context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
