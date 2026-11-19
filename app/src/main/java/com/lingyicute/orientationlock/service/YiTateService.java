package com.lingyicute.orientationlock.service;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
    private static final long OVERLAY_PERMISSION_POLL_MS = 5_000;
    public static final String ACTION_SYNC_ORIENTATION = BuildConfig.APPLICATION_ID + ".action.SYNC_ORIENTATION";

    private PreferenceManager preferences;
    private Space holderView;
    private int latestStartId;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private String lastChannelName;
    private String lastNotificationTitle;
    private String lastNotificationText;
    private final SharedPreferences.OnSharedPreferenceChangeListener orientationListener = (store, key) -> {
        if (PreferenceManager.KEY_ORIENTATION.equals(key)
                && currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                && preferences.getOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopLock();
        }
    };

    private AppOpsManager overlayOps;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable checkOverlayPermission = () -> {
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                || PermissionUtils.isDrawOverlaysPermissionGranted(this)) return;
        if (preferences.getOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopLock();
        } else {
            failLock();
        }
    };
    private final Runnable pollOverlayPermission = this::pollOverlayPermission;
    private final AppOpsManager.OnOpChangedListener overlayListener =
            (op, packageName) -> mainHandler.post(checkOverlayPermission);

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                removeOrientationLayout();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())
                    && currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                syncLock(false);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getInstance(this);
        // API 21-25 also need a foreground service to keep the lock alive.
        setForeground();
        // Do not stop a queued foreground start from the Activity before we get here.
        // Active services observe Default immediately; queued starts read it in syncLock().
        preferences.registerListener(orientationListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        // These are protected system broadcasts, not app-defined broadcasts.
        registerReceiver(screenReceiver, filter);
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        // On API 21-22 the integer watcher can register, but Android only dispatches
        // this listener for ops with a public string name. Overlay has none until M.
        if (appOps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        getPackageName(), overlayListener);
                overlayOps = appOps;
            } catch (IllegalArgumentException | SecurityException e) {
                Log.w(TAG, "Unable to watch overlay permission", e);
            }
        }
    }

    private void scheduleOverlayPermissionPoll() {
        // Only poll when we cannot watch the op and there is an attached overlay.
        // Screen off, Default and onDestroy all cancel the check via removal.
        mainHandler.removeCallbacks(pollOverlayPermission);
        if (overlayOps == null && holderView != null) {
            mainHandler.postDelayed(pollOverlayPermission, OVERLAY_PERMISSION_POLL_MS);
        }
    }

    private void pollOverlayPermission() {
        checkOverlayPermission.run();
        scheduleOverlayPermissionPoll();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return;
        // There is no channel, and hence no remembered channel name, before API 26.
        boolean channelRenamed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getString(R.string.running_notification_channel_name).equals(lastChannelName);
        if (channelRenamed
                || !getString(R.string.running_notification_title).equals(lastNotificationTitle)
                || !getNotificationText().equals(lastNotificationText)) {
            setForeground();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (intent != null && !ACTION_SYNC_ORIENTATION.equals(intent.getAction())) {
            if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                stopLock();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }
        // A start request may arrive after a newer selection or an explicit stop. Sticky
        // recreation also has no original intent. Always reconcile the latest saved choice.
        return syncLock(true) ? START_STICKY : START_NOT_STICKY;
    }

    private boolean syncLock(boolean refreshNotification) {
        int previousOrientation = currentOrientation;
        currentOrientation = preferences.getOrientation();
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopLock();
            return false;
        }
        if (!applyOrientation()) return false;
        scheduleOverlayPermissionPoll();
        if (refreshNotification || currentOrientation != previousOrientation) setForeground();
        return true;
    }

    @Override
    public void onDestroy() {
        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        preferences.unregisterListener(orientationListener);
        unregisterReceiver(screenReceiver);
        AppOpsManager appOps = overlayOps;
        overlayOps = null;
        if (appOps != null) appOps.stopWatchingMode(overlayListener);
        mainHandler.removeCallbacks(checkOverlayPermission);
        removeOrientationLayout();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = getString(R.string.running_notification_channel_name);
            if (channelName.equals(lastChannelName)) {
                return;
            }
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_ID,
                    channelName, NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            lastChannelName = channelName;
        }
    }

    private void setForeground() {
        ensureNotificationChannel();
        String title = getString(R.string.running_notification_title);
        String text = getNotificationText();
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_ID)
                : new Notification.Builder(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_orientation)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        lastNotificationTitle = title;
        lastNotificationText = text;
        try {
            startForeground(1, notification);
        } catch (IllegalStateException | SecurityException e) {
            // API 31+ rejects a background start, e.g. a sticky restart after the overlay
            // permission (the exemption this app relies on) was revoked while the process was
            // dead. Clear the lock and let the pending command stop the service instead.
            Log.w(TAG, "Unable to run in the foreground", e);
            if (preferences.getOrientation() == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                stopLock(); // Nothing to report: the user already returned to Default.
            } else {
                failLock();
            }
        }
    }

    /** The notification is the only place a locked mode is visible outside the app. */
    private String getNotificationText() {
        int label = OrientationUtils.getLabelRes(currentOrientation);
        return label == 0
                ? getString(R.string.running_notification_description)
                : getString(R.string.running_notification_mode, getString(label));
    }

    private boolean applyOrientation() {
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
            if (holderView != null) {
                try {
                    windowManager.updateViewLayout(holderView, params);
                    return true;
                } catch (IllegalArgumentException e) {
                    // The system dropped the window behind our back; attach a new one.
                    Log.w(TAG, "Overlay was detached; re-attaching", e);
                    holderView = null;
                }
            }
            Space view = new Space(this);
            view.setVisibility(android.view.View.VISIBLE);
            windowManager.addView(view, params);
            holderView = view; // Only remember successfully attached views.
            return true;
        } catch (SecurityException | WindowManager.BadTokenException e) {
            // Permission can be revoked between the check and add/updateViewLayout.
            Log.w(TAG, "Unable to apply orientation overlay", e);
            failLock();
            return false;
        }
    }

    private void failLock() {
        stopLock();
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        Toast.makeText(this, R.string.orientation_failed, Toast.LENGTH_LONG).show();
    }

    private void stopLock() {
        currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        removeOrientationLayout();
        // A later start may already be queued. Do not stop it or demote its service.
        stopSelf(latestStartId);
    }

    private void removeOrientationLayout() {
        mainHandler.removeCallbacks(pollOverlayPermission);
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
