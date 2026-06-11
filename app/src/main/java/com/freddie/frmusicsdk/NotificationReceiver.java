package com.freddie.frmusicsdk;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case "ACTION_1":
                    handleAction(context);
                    break;
                case "ACTION_2":
                    handleAction(context);
                    break;
                case "ACTION_3":
                    handleAction(context);
                    break;
                case "ACTION_4":
                    handleAction(context);
                    break;
            }
        }
    }

    private void handleAction(Context context) {
        safeStartActivity(context, new Intent(context, MainActivity.class));
    }

    // Unified safe start activity method
    private void safeStartActivity(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
        } catch (Exception e) {
            // Handle exception (e.g., log or show a toast)
        }
    }
}
