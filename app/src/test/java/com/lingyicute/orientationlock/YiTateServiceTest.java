package com.lingyicute.orientationlock;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.service.YiTateService;
import com.lingyicute.orientationlock.utils.OrientationUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadows.ShadowWindowManagerImpl;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34})
public class YiTateServiceTest {
    private ServiceController<YiTateService> controller;
    private YiTateService service;
    private Context context;
    private PreferenceManager preferences;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ReflectionHelpers.setStaticField(PreferenceManager.class, "sManager", null);
        preferences = PreferenceManager.getInstance(context);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW);
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            shadowOf(appOps).setMode(24, Process.myUid(), context.getPackageName(),
                    AppOpsManager.MODE_ALLOWED);
        } else {
            ShadowSettings.setCanDrawOverlays(true);
        }
        shadowOf((PowerManager) context.getSystemService(Context.POWER_SERVICE)).setIsInteractive(true);
        controller = Robolectric.buildService(YiTateService.class).create();
        service = controller.get();
    }

    @After
    public void tearDown() {
        controller.destroy();
    }

    private int select(int orientation) {
        preferences.setOrientation(orientation);
        return dispatchSelection(1);
    }

    private int dispatchSelection(int startId) {
        return service.onStartCommand(new Intent(context, YiTateService.class)
                .setAction(YiTateService.ACTION_SYNC_ORIENTATION), 0, startId);
    }

    private View overlay() {
        return ReflectionHelpers.getField(service, "holderView");
    }

    private void screen(boolean on) {
        shadowOf((PowerManager) context.getSystemService(Context.POWER_SERVICE)).setIsInteractive(on);
        context.sendBroadcast(new Intent(on ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF));
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void serviceIsNotExported() throws Exception {
        assertFalse(context.getPackageManager().getServiceInfo(
                new ComponentName(context, YiTateService.class), 0).exported);
    }

    @Test
    public void selectingDefaultRemovesOverlayAndStops() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        assertNotNull(overlay());
        assertEquals(Service.START_NOT_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void activeLockStopsImmediatelyWhenPreferenceReturnsToDefault() {
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertNotNull(overlay());
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Implements(Service.class)
    public static class RejectableForeground extends ShadowService {
        boolean reject;

        @Override
        @Implementation
        protected void startForeground(int id, Notification notification) {
            if (reject) throw new SecurityException("Foreground promotion rejected");
            super.startForeground(id, notification);
        }
    }

    @Test
    @Config(shadows = RejectableForeground.class)
    public void rejectedForegroundRefreshDoesNotRequestAStickyRestart() {
        RejectableForeground foreground = Shadow.extract(service);
        foreground.reject = true; // onCreate succeeded; fail the command's refresh.
        assertEquals(Service.START_NOT_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void stickyRestartRestoresSavedOrientation() {
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1));
        assertNotNull(overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ((WindowManager.LayoutParams) overlay().getLayoutParams()).screenOrientation);
        assertEquals("Locked: Reverse portrait", notificationText());
    }

    @Test
    public void emptyStickyRestartStopsWithoutOverlay() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void repeatedSelectionReusesNonTouchableOverlay() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        View first = overlay();
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        assertSame(first, overlay());
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) first.getLayoutParams();
        assertEquals(View.VISIBLE, first.getVisibility());
        assertEquals(0, params.width);
        assertEquals(0, params.height);
        assertNotEquals(0, params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    @Test
    public void detachedOverlayIsReattachedOnNextSelection() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        View first = overlay();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).removeViewImmediate(first);
        assertEquals(Service.START_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertNotNull(overlay());
        assertNotSame(first, overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ((WindowManager.LayoutParams) overlay().getLayoutParams()).screenOrientation);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, preferences.getOrientation());
    }

    @Test
    public void screenOffRemovesAndScreenOnRestoresOverlay() {
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        screen(false);
        assertNull(overlay());
        screen(true);
        assertNotNull(overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ((WindowManager.LayoutParams) overlay().getLayoutParams()).screenOrientation);
    }

    @Test
    public void selectionWhileScreenOffIsDeferredUntilScreenOn() {
        screen(false);
        assertEquals(Service.START_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        assertNull(overlay());
        screen(true);
        assertNotNull(overlay());
    }

    @Test
    public void permissionRevocationOnWakeClearsLock() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        screen(false);
        ShadowSettings.setCanDrawOverlays(false);
        screen(true);
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void appOpRevocationClearsTheLockWithoutWaitingForWakeOrActivity() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        assertNotNull(overlay());
        ShadowSettings.setCanDrawOverlays(false);
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(),
                context.getPackageName(), AppOpsManager.MODE_ERRORED);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    @Config(sdk = {21, 22})
    public void legacyAppOpRevocationClearsAnAttachedLockOnTheNextPoll() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int op = 24; // SYSTEM_ALERT_WINDOW before its public string name.
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        assertNotNull(overlay());

        shadowOf(appOps).setMode(op, Process.myUid(), context.getPackageName(),
                AppOpsManager.MODE_ERRORED);
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(overlay()); // AOSP Lollipop does not deliver a public listener callback.
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS);

        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    @Config(sdk = {21, 22})
    public void legacyPollingStopsWhileScreenIsOffAndWakeRechecksPermission() {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int op = 24;
        assertEquals(Service.START_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        screen(false);
        assertNull(overlay());

        shadowOf(appOps).setMode(op, Process.myUid(), context.getPackageName(),
                AppOpsManager.MODE_ERRORED);
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, preferences.getOrientation());

        screen(true);
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Implements(className = "android.view.WindowManagerImpl", isInAndroidSdk = false)
    public static class RemovedDisplay extends ShadowWindowManagerImpl {
        @Override
        @Implementation
        public void addView(View view, ViewGroup.LayoutParams params) {
            throw new WindowManager.InvalidDisplayException("Display removed");
        }
    }

    @Test
    @Config(shadows = RemovedDisplay.class)
    public void losingTheDisplayDuringAttachmentClearsTheLock() {
        assertEquals(Service.START_NOT_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void missingPermissionDoesNotAttemptToAttachOverlay() {
        ShadowSettings.setCanDrawOverlays(false);
        assertEquals(Service.START_NOT_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
    }

    @Test
    public void invalidCommandCannotReplaceActiveLock() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        View first = overlay();
        service.onStartCommand(new Intent(context, YiTateService.class), 0, 2);
        assertSame(first, overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, preferences.getOrientation());
    }

    @Test
    public void invalidInitialCommandStopsWithoutDiscardingSavedChoice() {
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals(Service.START_NOT_STICKY,
                service.onStartCommand(new Intent(context, YiTateService.class), 0, 1));
        assertNull(overlay());
        assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, preferences.getOrientation());
    }

    @Test
    public void screenOnCannotResurrectARejectedStartBeforeDestruction() {
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals(Service.START_NOT_STICKY,
                service.onStartCommand(new Intent(context, YiTateService.class), 0, 1));
        // stopSelf(startId) is asynchronous; the receiver is still registered.
        screen(true);
        assertNull(overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, preferences.getOrientation());
        assertEquals(Service.START_STICKY, dispatchSelection(2));
        assertNotNull(overlay());
    }

    @Test
    public void queuedStartsApplyOnlyTheLatestSelection() {
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // The first start has not reached our process yet when the user changes modes.
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals(Service.START_STICKY, dispatchSelection(1));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ((WindowManager.LayoutParams) overlay().getLayoutParams()).screenOrientation);

        // The user then picks Default before command 2 arrives.
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        assertEquals(Service.START_NOT_STICKY, dispatchSelection(2));
        assertNull(overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void corruptPreferenceFallsBackToDefault() {
        preferences.setOrientation(999);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("orientation", "bad migration").commit();
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
    }

    @Test
    public void modeValidationAcceptsOnlyTheEightUiModes() {
        int count = 0;
        for (int i = -10; i <= 100; i++) {
            if (!OrientationUtils.isSupported(i)) continue;
            count++;
            if (i != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                assertNotEquals(0, OrientationUtils.getLabelRes(i));
            }
        }
        assertEquals(8, count);
    }

    @Test
    @Config(sdk = {21, 25, 28, 34})
    public void notificationIsForegroundOnAllSupportedApiGenerations() {
        assertNotNull(shadowOf(service).getLastForegroundNotification());
        assertEquals(1, shadowOf(service).getLastForegroundNotificationId());
        assertTrue(context.getDrawable(R.drawable.ic_stat_orientation).getIntrinsicHeight() > 0);
    }

    @Test
    @Config(sdk = 34)
    public void foregroundNotificationOptsOutOfDeferralOnAndroid12Plus() {
        Notification notification = shadowOf(service).getLastForegroundNotification();
        assertEquals(Notification.FOREGROUND_SERVICE_IMMEDIATE,
                (int) ReflectionHelpers.getField(notification, "mFgsDeferBehavior"));
    }

    @Test
    public void refreshingThePersistentNotificationDoesNotRepeatItsAlert() {
        select(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        Notification notification = shadowOf(service).getLastForegroundNotification();
        assertEquals("Locked: Portrait", notificationText());
        assertNotEquals(0, notification.flags & Notification.FLAG_ONLY_ALERT_ONCE);
    }

    @Test
    public void notificationReportsTheLockedMode() {
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals("Locked: Portrait", notificationText());
        select(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        assertEquals("Locked: Sensor landscape", notificationText());
    }

    @Test
    public void notificationRefreshesWhenSystemLocaleChanges() {
        RuntimeEnvironment.setQualifiers("zh-rCN");
        service.onConfigurationChanged(service.getResources().getConfiguration());
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals("运行状态通知", ReflectionHelpers.getField(service, "lastChannelName"));
        assertEquals("已锁定：竖屏", notificationText());
        RuntimeEnvironment.setQualifiers("");
        service.onConfigurationChanged(service.getResources().getConfiguration());
        assertEquals("Running Notification", ReflectionHelpers.getField(service, "lastChannelName"));
        assertEquals("Locked: Portrait", notificationText());
        android.app.Notification initial = shadowOf(service).getLastForegroundNotification();
        service.onConfigurationChanged(service.getResources().getConfiguration());
        assertSame(initial, shadowOf(service).getLastForegroundNotification());
        RuntimeEnvironment.setQualifiers("zh-rCN");
        service.onConfigurationChanged(service.getResources().getConfiguration());
        assertNotSame(initial, shadowOf(service).getLastForegroundNotification());
        assertEquals("运行状态通知", ReflectionHelpers.getField(service, "lastChannelName"));
        assertEquals("已锁定：竖屏", notificationText());
    }

    @Test
    @Config(sdk = {21, 25})
    public void unchangedConfigurationDoesNotRepostTheNotificationWithoutAChannel() {
        assertEquals(Service.START_STICKY, select(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT));
        android.app.Notification initial = shadowOf(service).getLastForegroundNotification();
        // Sensor modes still rotate; each rotation reaches the service as a configuration change.
        service.onConfigurationChanged(service.getResources().getConfiguration());
        assertSame(initial, shadowOf(service).getLastForegroundNotification());
        RuntimeEnvironment.setQualifiers("zh-rCN");
        service.onConfigurationChanged(service.getResources().getConfiguration());
        assertNotSame(initial, shadowOf(service).getLastForegroundNotification());
        assertEquals("已锁定：感应竖屏", notificationText());
    }

    @Test
    public void notificationReopensTheActivityAsANewTaskLaunch() {
        Intent launch = shadowOf(shadowOf(service).getLastForegroundNotification().contentIntent)
                .getSavedIntent();
        assertEquals(MainActivity.class.getName(), launch.getComponent().getClassName());
        int required = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP;
        assertEquals(required, launch.getFlags() & required);
    }

    @Test
    public void notificationFallsBackToTheGenericDescriptionWithoutALock() {
        assertEquals("YiTate is running to keep the screen orientation", notificationText());
    }

    private String notificationText() {
        ShadowNotification notification = shadowOf(shadowOf(service).getLastForegroundNotification());
        return notification.getContentText().toString();
    }
}
