package com.clawos.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class WsKeepAliveService extends Service {

    private static final String TAG = "WsKeepAlive";
    private static final String CHANNEL_ID = "ws_keepalive_v2";
    private static final int NOTIFICATION_ID = 1;
    private static WsKeepAliveService instance;
    private PowerManager.WakeLock wakeLock;
    private MediaPlayer silentPlayer;
    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        acquireWakeLock();
        initMediaSession();
        startSilentAudio();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("Keeping connection alive...");

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopSilentAudio();
        releaseMediaSession();
        releaseWakeLock();
        super.onDestroy();
    }

    /**
     * Update the foreground notification text. Called from JS via TaskNotificationPlugin.
     */
    public static void updateStatus(String text) {
        WsKeepAliveService svc = instance;
        if (svc == null) return;

        NotificationManager mgr = svc.getSystemService(NotificationManager.class);
        if (mgr != null) {
            mgr.notify(NOTIFICATION_ID, svc.buildNotification(text));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(String text) {
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setAction(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Background Connection",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Keeps WebSocket connections alive in the background");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "Shell_KeepAlive");
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        getApplicationInfo().loadLabel(getPackageManager()).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Background Service")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
                .build());
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .build());
        mediaSession.setActive(true);
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    private void startSilentAudio() {
        try {
            silentPlayer = MediaPlayer.create(this, R.raw.silence);
            if (silentPlayer != null) {
                silentPlayer.setLooping(true);
                silentPlayer.setVolume(0f, 0f);
                silentPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());
                silentPlayer.start();
                Log.d(TAG, "Silent audio started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start silent audio", e);
        }
    }

    private void stopSilentAudio() {
        if (silentPlayer != null) {
            try {
                silentPlayer.stop();
                silentPlayer.release();
            } catch (Exception e) {
                // ignore
            }
            silentPlayer = null;
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Shell::WsKeepAlive");
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
