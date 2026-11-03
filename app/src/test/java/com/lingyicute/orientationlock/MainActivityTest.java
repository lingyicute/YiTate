package com.lingyicute.orientationlock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.utils.ContextUtils;
import com.lingyicute.orientationlock.utils.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPorterDuffColorFilter;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    private PreferenceManager preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        ReflectionHelpers.setStaticField(PreferenceManager.class, "sManager", null);
        preferences = PreferenceManager.getInstance(context);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
        ShadowSettings.setCanDrawOverlays(false);
    }

    @Test
    public void grantingOverlayPermissionCompletesPendingSelection() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            controller.pause();
            ShadowSettings.setCanDrawOverlays(true);
            controller.resume();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, preferences.getOrientation());
            assertTrue(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
        }
    }

    @Test
    public void deniedOverlayPermissionKeepsDefaultSelection() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.tv_orientation_portrait).performClick();
            controller.pause().resume();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            assertTrue(controller.get().findViewById(R.id.tv_orientation_default).isSelected());
        }
    }

    @Test
    public void pendingSelectionSurvivesActivityRecreation() {
        Bundle state = new Bundle();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.tv_orientation_reverse_portrait).performClick();
            controller.pause().saveInstanceState(state);
        }
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create(state).start().resume().visible()) {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, preferences.getOrientation());
        }
    }

    @Test
    @Config(sdk = {21, 28, 34})
    public void iconsAreTintedWithTheThemeAccent() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            Activity activity = controller.get();
            int accent = ContextUtils.getColorFromAttr(activity, android.R.attr.colorAccent);
            assertEquals(0xFF, accent >>> 24);
            Drawable icon = ((TextView) activity.findViewById(R.id.tv_orientation_landscape))
                    .getCompoundDrawables()[1];
            assertNotNull(icon);
            // VectorDrawable did not override Drawable.getColorFilter() on API 21.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                assertTrue(icon.getColorFilter() instanceof PorterDuffColorFilter);
                ShadowPorterDuffColorFilter filter = shadowOf((PorterDuffColorFilter) icon.getColorFilter());
                assertEquals(accent, filter.getColor());
                assertEquals(PorterDuff.Mode.SRC_IN, filter.getMode());
            }
        }
    }

    @Test
    public void returningAfterRevocationResetsSavedAndDisplayedOrientation() {
        ShadowSettings.setCanDrawOverlays(true);
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.pause();
            ShadowSettings.setCanDrawOverlays(false);
            controller.resume();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            assertTrue(controller.get().findViewById(R.id.tv_orientation_default).isSelected());
        }
    }

    @Test
    @Config(sdk = 34)
    public void firstLaunchRequestsNotificationPermissionOnce() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNotNull(shadowOf(activity).getLastRequestedPermission());
            // An unanswered prompt must not be recorded as a refusal.
            assertFalse(preferences.isNotificationPermissionRequested());
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            assertTrue(preferences.isNotificationPermissionRequested());
        }
    }

    @Test
    @Config(sdk = 34)
    public void permanentlyDeniedNotificationPermissionIsNotRequestedAgain() {
        preferences.setNotificationPermissionRequested(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNull(shadowOf(controller.get()).getLastRequestedPermission());
        }
    }

    @Test
    @Config(sdk = 34)
    public void notificationRationaleDialogIsShownAtMostOnce() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        try (ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(first.get(), "rationaleDialog");
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            // Not answered yet, so it must not be recorded.
            assertFalse(preferences.isNotificationRationaleShown());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            // AlertDialog delivers button clicks through a Handler message.
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(preferences.isNotificationRationaleShown());
        }
        try (ActivityController<MainActivity> second = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNull(ReflectionHelpers.getField(second.get(), "rationaleDialog"));
            assertNull(shadowOf(second.get()).getLastRequestedPermission());
        }
    }

    @Test
    public void overlayPermissionRequestFallsBackToApplicationDetailsSettings() {
        List<Intent> started = new ArrayList<>();
        Context wrapper = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public void startActivity(Intent intent) {
                started.add(intent);
                if (Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(intent.getAction())) {
                    throw new ActivityNotFoundException();
                }
            }
        };
        PermissionUtils.requestDrawOverlaysPermission(wrapper);
        assertEquals(3, started.size());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.get(2).getAction());
        assertEquals(Uri.parse("package:" + wrapper.getPackageName()), started.get(2).getData());
    }

    @Test
    public void overlayPermissionRequestFallsBackWhenOverlaySettingsThrowsSecurityException() {
        List<Intent> started = new ArrayList<>();
        Context wrapper = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public void startActivity(Intent intent) {
                started.add(intent);
                if (Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(intent.getAction())) {
                    throw new SecurityException("Blocked by OEM settings");
                }
            }
        };
        PermissionUtils.requestDrawOverlaysPermission(wrapper);
        assertEquals(3, started.size());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.get(2).getAction());
    }

    @Test
    @Config(sdk = 34)
    public void unansweredNotificationRationaleDialogSurvivesRecreation() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        Bundle state = new Bundle();
        try (ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(first.get(), "rationaleDialog");
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            first.pause().saveInstanceState(state);
        }
        assertFalse(preferences.isNotificationRationaleShown());
        try (ActivityController<MainActivity> recreated = Robolectric.buildActivity(MainActivity.class)
                .create(state).start().resume().visible()) {
            AlertDialog dialog = ReflectionHelpers.getField(recreated.get(), "rationaleDialog");
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
        }
    }

    @Test
    @Config(sdk = 34)
    public void grantingNotificationPermissionInSettingsReconcilesOnResume() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(controller.get(), "rationaleDialog");
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            controller.pause();
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
            controller.resume();
            assertFalse(preferences.isNotificationPermissionRequested());
            assertFalse(preferences.isNotificationRationaleShown());
            assertNull(ReflectionHelpers.getField(controller.get(), "rationaleDialog"));
        }
    }

    @Test
    public void selectingDefaultCancelsPendingOrientationAndRevocationResetsOnClick() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, preferences.getOrientation());

            ShadowSettings.setCanDrawOverlays(false);
            activity.findViewById(R.id.tv_orientation_portrait).performClick();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());

            activity.findViewById(R.id.tv_orientation_default).performClick();
            controller.pause();
            ShadowSettings.setCanDrawOverlays(true);
            controller.resume();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
        }
    }

    @Test
    @Config(sdk = 34)
    public void grantingNotificationPermissionClearsRefusalFlags() {
        preferences.setNotificationPermissionRequested(true);
        preferences.setNotificationRationaleShown(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
            assertFalse(preferences.isNotificationPermissionRequested());
            assertFalse(preferences.isNotificationRationaleShown());
        }
    }
}
