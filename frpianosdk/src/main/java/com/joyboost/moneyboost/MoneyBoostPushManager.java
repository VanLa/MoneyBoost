package com.joyboost.moneyboost;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

public class MoneyBoostPushManager {

    private static final String CHANNEL_ID = "game_notifications_channel";

    public static MoneyBoostPushManager instance;

    public static MoneyBoostPushManager instance() {
        if (null == instance) {
            instance = new MoneyBoostPushManager();
        }
        return instance;
    }

    // 合并创建通知通道和设置推送的方法
    public static void createNotificationAndSchedulePush(Context context, long startTimeInMillis, int intervalDays,
                                                         String title, String content, String iconName, String targetActivity) {
        // 创建通知通道（如果没有创建过）
        createNotificationChannel(context);
        requestNotificationPermission(context);  // 请求通知权限（只在Android 13及以上需要）

        // 设置推送
        long triggerTime = getNextTriggerTime(startTimeInMillis, intervalDays);

        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("content", content);
        intent.putExtra("iconName", iconName);
        intent.putExtra("targetActivity", targetActivity);  // 传递目标Activity的类名
        intent.setAction("com.joyboost.moneyboost.SEND_NOTIFICATION"); // 添加 Action
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // 使用非精确重复闹钟，避免使用敏感权限
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    AlarmManager.INTERVAL_DAY * intervalDays,
                    pendingIntent
            );
        }
    }


    // 创建通知通道（只在没有创建时才创建）
    private static void createNotificationChannel(Context context) {
        // 如果 SDK >= 26 (Android 8.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // 判断是否已经创建过通知通道
            if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                CharSequence name = "Game Notifications";
                String description = "Notifications for game events";
                int importance = NotificationManager.IMPORTANCE_DEFAULT;

                // 创建通知通道
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);

                // 创建通道
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // 请求通知权限（适用于 Android 13 及以上）
    private static void requestNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (context instanceof Activity) {
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
            }
        }
    }

    // 计算下一个触发时间
    private static long getNextTriggerTime(long startTimeInMillis, int intervalDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startTimeInMillis);

        // 如果当前时间已经超过指定时间，则计算下次推送的时间
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, intervalDays);  // 间隔天数
        }

        return calendar.getTimeInMillis();
    }

    // 广播接收器，用于接收并显示通知
    public static class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra("title");
            String content = intent.getStringExtra("content");
            String iconName = intent.getStringExtra("iconName"); // 获取图标名称
            String targetActivity = intent.getStringExtra("targetActivity"); // 获取目标 Activity 类名

            showNotification(context, title, content, iconName, targetActivity);
        }

        // 显示通知
        private void showNotification(Context context, String title, String content, String iconName, String targetActivity) {
            // 根据目标 Activity 类名动态构造 Intent
            try {
                Class<?> targetClass = Class.forName(targetActivity);
                Intent notificationIntent = new Intent(context, targetClass); // 动态获取目标Activity
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true) // 点击后自动关闭通知
                        .setContentIntent(pendingIntent); // 设置点击事件

                // 根据传入的 iconName 来加载图标
                int iconResId = context.getResources().getIdentifier(iconName, "mipmap", context.getPackageName());
                if (iconResId != 0) {
                    builder.setSmallIcon(iconResId);
                } else {
                    builder.setSmallIcon(android.R.drawable.ic_notification_overlay); // 如果没有找到图标，使用默认图标
                }

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                // 发送通知
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // 可选：打印日志方便调试
                    // Log.w("AmericanoPushManager", "Notification permission not granted, skipping notification.");
                    return;
                }
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            } catch (ClassNotFoundException e) {
            }
        }
    }
}
