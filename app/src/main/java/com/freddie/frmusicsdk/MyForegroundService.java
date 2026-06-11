package com.freddie.frmusicsdk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import java.util.Random;

public class MyForegroundService extends Service {
    private String dynamicChannelId;
    private final int notificationId;
    private final Random random = new Random();

    public MyForegroundService() {
        notificationId = generateFourDigitId();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        long timestamp = System.currentTimeMillis();
        dynamicChannelId = "dynamic_channel_" + timestamp;
        createNotificationChannel();
        startForeground(notificationId, buildNotification());
    }

    // 生成4位随机数（1000-9999）
    private int generateFourDigitId() {
        return random.nextInt(9000) + 1000;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    dynamicChannelId,
                    "Service-" + generateFourDigitId(),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background processing");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        // 每次构建时生成新组名
        String randomGroup = "group_" + generateFourDigitId() + "_" + (System.currentTimeMillis() % 1000);

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.sprunki_beats_foreground_notification);
        setButtonClick(remoteViews, R.id.btn1, "ACTION_1");
        setButtonClick(remoteViews, R.id.btn2, "ACTION_2");
        setButtonClick(remoteViews, R.id.btn3, "ACTION_3");
        setButtonClick(remoteViews, R.id.btn4, "ACTION_4");

        return new NotificationCompat.Builder(this, dynamicChannelId)
                .setSmallIcon(R.drawable.transparent_icon)
                .setCustomContentView(remoteViews)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setGroup(randomGroup)
                .build();
    }

    private void setButtonClick(RemoteViews remoteViews, int buttonId, String action) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.setAction(action);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        remoteViews.setOnClickPendingIntent(buttonId, pendingIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void start(Context context) {
        Intent serviceIntent = new Intent(context, MyForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}