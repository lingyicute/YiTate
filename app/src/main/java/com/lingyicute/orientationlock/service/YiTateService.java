package com.lingyicute.orientationlock.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Space;
import android.widget.Toast;

import com.lingyicute.orientationlock.BuildConfig;
import com.lingyicute.orientationlock.MainActivity;
import com.lingyicute.orientationlock.R;
import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.utils.OrientationUtils;
import com.lingyicute.orientationlock.utils.PermissionUtils;

public class YiTateService extends Service {
    private static final String TAG = YiTateService.class.getSimpleName();
    private static final String NOTIFICATION_ID = BuildConfig.APPLICATION_ID + ".notification";
    public static final String ACTION_SET_ORIENTATION = BuildConfig.APPLICATION_ID + ".action.SET_ORIENTATION";
    public static final String KEY_ORIENTATION = "orientation";

    private Space holderView;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                removeOrientationLayout();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                applyOrientation();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // API 21-25 also need a foreground service to keep the lock alive.
        setForeground();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // These are protected system broadcasts, not app-defined broadcasts.
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int orientation;
        if (intent == null) {
            // START_STICKY recreation does not redeliver the original intent.
            orientation = PreferenceManager.getInstance(this).getOrientation();
        } else if (ACTION_SET_ORIENTATION.equals(intent.getAction())) {
            orientation = intent.getIntExtra(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (!OrientationUtils.isSupported(orientation)) {
                if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) stopSelf();
                return currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        ? START_NOT_STICKY : START_STICKY;
            }
        } else {
            if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) stopSelf();
            return currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ? START_NOT_STICKY : START_STICKY;
        }
        currentOrientation = orientation;
        if (!applyOrientation()) return START_NOT_STICKY;
        PreferenceManager.getInstance(this).setOrientation(orientation);
        // Reuse the running notification to show which mode is locked.
        setForeground();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenReceiver);
        removeOrientationLayout();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_ID,
                    getString(R.string.running_notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, NOTIFICATION_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = builder
                .setContentTitle(getString(R.string.running_notification_title))
                .setContentText(getNotificationText())
                .setSmallIcon(R.drawable.ic_stat_orientation)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    /** The notification is the only place a locked mode is visible outside the app. */
    private String getNotificationText() {
        int label = OrientationUtils.getLabelRes(currentOrientation);
        return label == 0
                ? getString(R.string.running_notification_description)
                : getString(R.string.running_notification_mode, getString(label));
    }

    private boolean applyOrientation() {
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            clearLock();
            return false; // Do not recreate an overlay after removing it.
        }
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            failLock();
            return false;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (!powerManager.isInteractive()) {
            removeOrientationLayout();
            return true; // Keep the requested orientation for SCREEN_ON.
        }
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(0, 0,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.screenOrientation = currentOrientation;
        try {
            if (holderView == null) {
                Space view = new Space(this);
                view.setVisibility(android.view.View.VISIBLE);
                windowManager.addView(view, params);
                holderView = view; // Only remember successfully attached views.
            } else {
                windowManager.updateViewLayout(holderView, params);
            }
            return true;
        } catch (SecurityException | WindowManager.BadTokenException e) {
            // Permission can be revoked between the check and add/updateViewLayout.
            Log.w(TAG, "Unable to apply orientation overlay", e);
            failLock();
            return false;
        }
    }

    private void failLock() {
        clearLock();
        Toast.makeText(this, R.string.orientation_failed, Toast.LENGTH_LONG).show();
    }

    private void clearLock() {
        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        PreferenceManager.getInstance(this).setOrientation(currentOrientation);
        removeOrientationLayout();
        stopForeground(true);
        stopSelf();
    }

    private void removeOrientationLayout() {
        if (holderView == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeViewImmediate(holderView);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Overlay was already detached", e);
        } finally {
            holderView = null;
        }
    }
}
