package com.clawos.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "TaskNotification")
public class TaskNotificationPlugin extends Plugin {

    private static final String CHANNEL_ID = "task_complete_v2";
    private static final String OLD_CHANNEL_ID = "task_complete";
    private static final int NOTIFY_ID = 20;

    @Override
    public void load() {
        NotificationManager mgr = getContext().getSystemService(NotificationManager.class);
        if (mgr == null) return;

        mgr.deleteNotificationChannel(OLD_CHANNEL_ID);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifications when tasks complete");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 300, 150, 300});
        channel.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        );
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        mgr.createNotificationChannel(channel);
    }

    @PluginMethod
    public void updateServiceStatus(PluginCall call) {
        String text = call.getString("text", "Keeping connection alive...");
        WsKeepAliveService.updateStatus(text);
        call.resolve();
    }

    @PluginMethod
    public void notify(PluginCall call) {
        String title = call.getString("title", "Task Complete");
        String body = call.getString("body", "Your request has been processed.");

        Intent launchIntent = new Intent(getContext(), MainActivity.class);
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        PendingIntent contentIntent = PendingIntent.getActivity(
                getContext(), 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);

        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                getContext(), 1, launchIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 300, 150, 300})
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenIntent, true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManager mgr = getContext().getSystemService(NotificationManager.class);
        if (mgr != null) {
            mgr.notify(NOTIFY_ID, builder.build());
        }
        call.resolve();
    }
}
