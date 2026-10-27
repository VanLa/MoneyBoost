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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** Unity supplies resolved copy and the first local timestamp; Android owns delivery. */
public final class MoneyBoostLocalPush {
    private static final String PREFS = "moneyboost_local_push_v1";
    private static final String CHANNEL = "game_notifications_channel";
    private static final String ACTION = "com.joyboost.moneyboost.LOCAL_PUSH";
    private static final int ID = 42001;
    private static volatile boolean foreground;

    public static void setForeground(boolean value) { foreground = value; }

    public static boolean register(Context context, long firstTimeMillis, int intervalDays, int fallbackHour,
                                   String messagesJson, String iconName) {
        try {
            if (firstTimeMillis <= 0 || intervalDays < 1 || intervalDays > 365 || fallbackHour < 8 || fallbackHour > 21)
                throw new IllegalArgumentException("Invalid push schedule");
            JSONArray messages = new JSONArray(messagesJson);
            if (messages.length() == 0) throw new IllegalArgumentException("Empty push library");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if (message.getString("title").trim().isEmpty() || message.getString("body").trim().isEmpty())
                    throw new IllegalArgumentException("Empty push copy");
            }
            createChannel(context);
            long next = nextAfter(firstTimeMillis, intervalDays, System.currentTimeMillis());
            Calendar local = Calendar.getInstance();
            local.setTimeInMillis(next);
            // Same PendingIntent replaces the existing alarm, including its localized payload.
            if (!schedule(context, next)) return false;
            prefs(context).edit().putBoolean("enabled", true).putLong("next", next)
                .putInt("days", intervalDays).putString("messages", messagesJson)
                .putInt("fallbackHour", fallbackHour)
                .putInt("hour", local.get(Calendar.HOUR_OF_DAY)).putInt("minute", local.get(Calendar.MINUTE))
                .putString("icon", iconName == null ? "" : iconName).commit();
            // Migrate the previous SDK API's single hard-coded notification.
            Intent legacy = new Intent(context, MoneyBoostPushManager.NotificationReceiver.class)
                .setAction("com.joyboost.moneyboost.SEND_NOTIFICATION");
            PendingIntent old = PendingIntent.getBroadcast(context, 0, legacy,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (old != null) {
                AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (alarms != null) alarms.cancel(old);
                old.cancel();
            }
            return true;
        } catch (Exception e) {
            Log.e("MoneyBoostLocalPush", "Registration failed", e);
            return false;
        }
    }

    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.runOnUiThread(() -> ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42001));
        }
    }

    public static void cancel(Context context) {
        prefs(context).edit().putBoolean("enabled", false).commit();
        PendingIntent pending = pending(context, PendingIntent.FLAG_NO_CREATE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (pending != null) {
            if (alarms != null) alarms.cancel(pending);
            pending.cancel();
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(ID);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static PendingIntent pending(Context context, int flags) {
        return PendingIntent.getBroadcast(context, ID,
            new Intent(context, Receiver.class).setAction(ACTION), flags | PendingIntent.FLAG_IMMUTABLE);
    }
    private static boolean schedule(Context context, long time) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return false;
        // Inexact alarm: no exact-alarm permission; Android may defer delivery in Doze.
        alarms.set(AlarmManager.RTC_WAKEUP, time, pending(context, PendingIntent.FLAG_UPDATE_CURRENT));
        return true;
    }
    static long nextAfter(long first, int days, long now) {
        return MoneyBoostPushClock.nextAfter(first, days, now);
    }
    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Game Notifications", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            SharedPreferences state = prefs(context);
            if (!state.getBoolean("enabled", false)) return;
            long now = System.currentTimeMillis();
            int days = state.getInt("days", 1);
            long planned = state.getLong("next", now);
            if (!ACTION.equals(intent.getAction())) {
                // Boot/update/time changes restore the alarm without sending a stale reminder.
                if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()) || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
                    Calendar local = Calendar.getInstance();
                    local.setTimeInMillis(planned);
                    local.set(Calendar.HOUR_OF_DAY, state.getInt("hour", 20));
                    local.set(Calendar.MINUTE, state.getInt("minute", 0));
                    local.set(Calendar.SECOND, 0);
                    local.set(Calendar.MILLISECOND, 0);
                    planned = local.getTimeInMillis();
                }
                long next = planned > now ? planned : MoneyBoostPushClock.nextFallback(planned, days,
                    state.getInt("fallbackHour", 20), now);
                Calendar saved = Calendar.getInstance();
                saved.setTimeInMillis(next);
                state.edit().putLong("next", next).putInt("hour", saved.get(Calendar.HOUR_OF_DAY))
                    .putInt("minute", saved.get(Calendar.MINUTE)).commit();
                schedule(context, next);
                return;
            }
            if (planned > now) { schedule(context, planned); return; }
            // No new open since Unity registered: the next day has no previous-day history.
            long next = MoneyBoostPushClock.nextFallback(planned, days, state.getInt("fallbackHour", 20), now);
            state.edit().putLong("next", next).putInt("hour", state.getInt("fallbackHour", 20))
                .putInt("minute", 0).commit();
            schedule(context, next);
            // Don't deliver yesterday's alarm late after a long device suspension.
            if (now - planned >= 6 * 60 * 60 * 1000L) return;
            if (foreground) return;
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            try {
                JSONArray messages = new JSONArray(state.getString("messages", "[]"));
                int cursor = state.getInt("cursor", 0);
                JSONObject message = messages.getJSONObject(Math.floorMod(cursor, messages.length()));
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                if (launch == null) return;
                launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                launch.putExtra("moneyboost_local_push", true);
                PendingIntent click = PendingIntent.getActivity(context, ID, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                String icon = state.getString("icon", "");
                int iconId = context.getResources().getIdentifier(icon, "drawable", context.getPackageName());
                if (iconId == 0) iconId = context.getResources().getIdentifier(icon, "mipmap", context.getPackageName());
                if (iconId == 0) iconId = android.R.drawable.ic_notification_overlay;
                createChannel(context);
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager == null) return;
                manager.notify(ID, new NotificationCompat.Builder(context, CHANNEL).setSmallIcon(iconId)
                    .setContentTitle(message.getString("title")).setContentText(message.getString("body"))
                    .setAutoCancel(true).setContentIntent(click).build());
                state.edit().putInt("cursor", (Math.floorMod(cursor, messages.length()) + 1) % messages.length()).apply();
            } catch (Exception e) { Log.e("MoneyBoostLocalPush", "Delivery failed", e); }
        }
    }
}
