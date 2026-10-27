package com.lingyicute.orientationlock;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
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
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

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
        ShadowSettings.setCanDrawOverlays(true);
        shadowOf((PowerManager) context.getSystemService(Context.POWER_SERVICE)).setIsInteractive(true);
        controller = Robolectric.buildService(YiTateService.class).create();
        service = controller.get();
    }

    @After
    public void tearDown() {
        controller.destroy();
    }

    private int select(int orientation) {
        return service.onStartCommand(new Intent(context, YiTateService.class)
                .setAction(YiTateService.ACTION_SET_ORIENTATION)
                .putExtra(YiTateService.KEY_ORIENTATION, orientation), 0, 1);
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
    public void stickyRestartRestoresSavedOrientation() {
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1));
        assertNotNull(overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ((WindowManager.LayoutParams) overlay().getLayoutParams()).screenOrientation);
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
        select(999);
        assertSame(first, overlay());
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, preferences.getOrientation());
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
        for (int i = -10; i <= 100; i++) if (OrientationUtils.isSupported(i)) count++;
        assertEquals(8, count);
    }

    @Test
    @Config(sdk = {21, 25, 28, 34})
    public void notificationIsForegroundOnAllSupportedApiGenerations() {
        assertNotNull(shadowOf(service).getLastForegroundNotification());
        assertEquals(1, shadowOf(service).getLastForegroundNotificationId());
    }

    @Test
    public void notificationReportsTheLockedMode() {
        select(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        assertEquals("Locked: Portrait", notificationText());
        select(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        assertEquals("Locked: Sensor landscape", notificationText());
    }

    @Test
    public void notificationFallsBackToTheGenericDescriptionWithoutALock() {
        assertEquals("YiTate is running for keeping the orientation", notificationText());
    }

    private String notificationText() {
        ShadowNotification notification = shadowOf(shadowOf(service).getLastForegroundNotification());
        return notification.getContentText().toString();
    }
}
