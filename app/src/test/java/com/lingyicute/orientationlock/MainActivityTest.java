package com.lingyicute.orientationlock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Space;
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
import org.robolectric.shadows.ShadowToast;
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
    public void selectingDefaultDoesNotCancelAQueuedForegroundStart() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertNotNull(shadowOf(activity).getNextStartedService());
            // The platform has accepted the start; onCreate/startForeground need not have run yet.
            activity.findViewById(R.id.tv_orientation_default).performClick();
            assertNull(shadowOf(activity).getNextStoppedService());
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
        }
    }

    @Test
    public void restartingActivityReconcilesSelectionChangedWhileStopped() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertTrue(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
            controller.pause().stop();
            preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            controller.start();
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
            assertFalse(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
        }
    }

    @Test
    public void activeActivityReflectsAServiceRollback() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertTrue(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
            preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
            assertFalse(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
        }
    }

    @Test
    @Config(sdk = 34)
    public void activeActivityResetsSelectionWhenPreferencesAreCleared() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertTrue(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
            activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
            assertFalse(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
        }
    }

    public static class RejectingActivity extends MainActivity {
        @Override
        public ComponentName startForegroundService(Intent intent) {
            throw new IllegalStateException("Foreground start not allowed");
        }
    }

    @Test
    public void rejectedForegroundStartDoesNotPersistOrShowALock() {
        ShadowSettings.setCanDrawOverlays(true);
        try (ActivityController<RejectingActivity> controller = Robolectric
                .buildActivity(RejectingActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, preferences.getOrientation());
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
            assertFalse(activity.findViewById(R.id.tv_orientation_landscape).isSelected());
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
    public void orientationChoicesAreFocusableButtonsWithWrappingLabels() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            Button choice = activity.findViewById(R.id.tv_orientation_reverse_landscape);
            assertTrue(choice.isFocusable());
            assertTrue(choice.getMaxLines() > 1);
            assertNull(choice.getEllipsize());
            assertTrue(activity.findViewById(R.id.iv_about) instanceof ImageButton);
        }
    }

    @Test
    public void dynamicSectionHeadingRemainsCenteredBetweenBalancedControls() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            LinearLayout root = activity.findViewById(R.id.ll_root);
            LinearLayout header = (LinearLayout) root.getChildAt(0);
            assertTrue(header.getChildAt(0) instanceof Space);
            TextView dynamicHeading = (TextView) header.getChildAt(1);
            ImageButton about = (ImageButton) header.getChildAt(2);
            assertEquals(Gravity.CENTER, dynamicHeading.getGravity());
            LinearLayout.LayoutParams startParams =
                    (LinearLayout.LayoutParams) header.getChildAt(0).getLayoutParams();
            LinearLayout.LayoutParams endParams =
                    (LinearLayout.LayoutParams) about.getLayoutParams();
            assertEquals(endParams.width, startParams.width);
            assertEquals(endParams.getMarginEnd(), startParams.getMarginStart());
        }
    }

    @Test
    public void aboutButtonHasRoomForItsIconWithoutClipping() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            ImageButton about = controller.get().findViewById(R.id.iv_about);
            Drawable icon = about.getDrawable();
            assertNotNull(icon);
            int width = about.getLayoutParams().width - about.getPaddingLeft() - about.getPaddingRight();
            int height = about.getLayoutParams().height - about.getPaddingTop() - about.getPaddingBottom();
            assertTrue(width >= icon.getIntrinsicWidth());
            assertTrue(height >= icon.getIntrinsicHeight());
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
            shadowOf(activity.getPackageManager()).setShouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS, true);
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            assertTrue(preferences.isNotificationPermissionRequested());
        }
    }

    @Test
    @Config(sdk = 34)
    public void dismissingTheFirstNotificationPromptDoesNotCountAsRefusal() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{PackageManager.PERMISSION_DENIED});
            assertFalse(preferences.isNotificationPermissionRequested());
        }
        try (ActivityController<MainActivity> next = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNotNull(shadowOf(next.get()).getLastRequestedPermission());
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
    @Config(sdk = {21, 22})
    public void legacyOverlayPermissionHonorsExplicitAppOpsDenials() {
        Context context = RuntimeEnvironment.getApplication();
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW);
        int op = 24; // SYSTEM_ALERT_WINDOW before its public string name.
        for (int mode : new int[]{AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_DEFAULT}) {
            shadowOf(appOps).setMode(op, Process.myUid(), context.getPackageName(), mode);
            assertTrue(PermissionUtils.isDrawOverlaysPermissionGranted(context));
        }
        for (int mode : new int[]{AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED}) {
            shadowOf(appOps).setMode(op, Process.myUid(), context.getPackageName(), mode);
            assertFalse(PermissionUtils.isDrawOverlaysPermissionGranted(context));
        }
    }

    @Test
    @Config(sdk = {21, 22})
    public void legacyAllowedAppOpCannotReplaceTheInstallTimePermission() {
        Context context = RuntimeEnvironment.getApplication();
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.SYSTEM_ALERT_WINDOW);
        for (int mode : new int[]{AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_DEFAULT}) {
            shadowOf(appOps).setMode(24, Process.myUid(), context.getPackageName(), mode);
            assertFalse(PermissionUtils.isDrawOverlaysPermissionGranted(context));
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
        assertTrue(PermissionUtils.requestDrawOverlaysPermission(wrapper));
        assertEquals(3, started.size());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.get(2).getAction());
        assertEquals(Uri.parse("package:" + wrapper.getPackageName()), started.get(2).getData());
        assertNotEquals(0, started.get(2).getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
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
        assertTrue(PermissionUtils.requestDrawOverlaysPermission(wrapper));
        assertEquals(3, started.size());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.get(2).getAction());
    }

    public static class BlockedBrowserActivity extends MainActivity {
        RuntimeException browserFailure;

        @Override
        public void startActivity(Intent intent) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) throw browserFailure;
            super.startActivity(intent);
        }
    }

    @Test
    public void unavailableAndRestrictedBrowsersGiveFeedbackWithoutClosingTheActivity() {
        try (ActivityController<BlockedBrowserActivity> controller = Robolectric
                .buildActivity(BlockedBrowserActivity.class).setup()) {
            BlockedBrowserActivity activity = controller.get();
            for (RuntimeException failure : new RuntimeException[]{
                    new ActivityNotFoundException(), new SecurityException("Restricted profile")}) {
                activity.browserFailure = failure;
                activity.findViewById(R.id.iv_about).performClick();
                assertEquals(activity.getString(R.string.about_link_unavailable), ShadowToast.getTextOfLatestToast());
                assertFalse(activity.isFinishing());
            }
        }
    }

    public static class SettingsBlockedActivity extends MainActivity {
        @Override
        public void startActivity(Intent intent) {
            throw new ActivityNotFoundException();
        }
    }

    @Test
    public void unavailableOverlaySettingsDoNotLeaveAPendingSelection() {
        try (ActivityController<SettingsBlockedActivity> controller = Robolectric
                .buildActivity(SettingsBlockedActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.tv_orientation_landscape).performClick();
            assertNull(ReflectionHelpers.getField(activity, "pendingOrientation"));
            assertTrue(activity.findViewById(R.id.tv_orientation_default).isSelected());
        }
    }

    @Test
    public void overlaySettingsLaunchedByTheActivityStayOnItsBackStack() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertTrue(PermissionUtils.requestDrawOverlaysPermission(controller.get()));
            Intent launched = shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, launched.getAction());
            assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
        }
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
    public void interruptedRequestAfterRationaleCanBeRetried() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            AlertDialog dialog = ReflectionHelpers.getField(activity, "rationaleDialog");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(shadowOf(activity).getLastRequestedPermission());
            activity.onRequestPermissionsResult(100, new String[0], new int[0]);
            assertFalse(preferences.isNotificationRationaleShown());
        }
        try (ActivityController<MainActivity> next = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(next.get(), "rationaleDialog");
            assertTrue(dialog.isShowing());
        }
    }

    @Test
    @Config(sdk = 34)
    public void dismissingTheNotificationRetryLeavesTheRationaleAvailable() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            AlertDialog dialog = ReflectionHelpers.getField(activity, "rationaleDialog");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{PackageManager.PERMISSION_DENIED});
            assertFalse(preferences.isNotificationRationaleShown());
        }
        try (ActivityController<MainActivity> next = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(next.get(), "rationaleDialog");
            assertTrue(dialog.isShowing());
        }
    }

    @Test
    @Config(sdk = 34)
    public void denialAfterRationaleIsRecordedAcrossRecreation() {
        preferences.setNotificationPermissionRequested(true);
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true);
        Bundle state = new Bundle();
        try (ActivityController<MainActivity> first = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog dialog = ReflectionHelpers.getField(first.get(), "rationaleDialog");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            first.pause().saveInstanceState(state);
        }
        try (ActivityController<MainActivity> next = Robolectric.buildActivity(MainActivity.class)
                .create(state).start().resume().visible()) {
            assertNull(ReflectionHelpers.getField(next.get(), "rationaleDialog"));
            shadowOf(next.get().getPackageManager()).setShouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS, false);
            next.get().onRequestPermissionsResult(100, new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{PackageManager.PERMISSION_DENIED});
            assertTrue(preferences.isNotificationRationaleShown());
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(ReflectionHelpers.getField(controller.get(), "requestingFromRationale"));
            controller.pause();
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
            controller.resume();
            assertFalse(ReflectionHelpers.getField(controller.get(), "requestingFromRationale"));
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

    @Test
    @Config(sdk = 34)
    public void grantingNotificationPermissionDoesNotOpenOverlaySettingsAfterRevocation() {
        ShadowSettings.setCanDrawOverlays(true);
        preferences.setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            while (shadowOf(activity).getNextStartedActivity() != null) {
                // Drain intents started while the activity came up.
            }
            ShadowSettings.setCanDrawOverlays(false);
            activity.onRequestPermissionsResult(100,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
            Intent next = shadowOf(activity).getNextStartedActivity();
            assertTrue(next == null || !Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(next.getAction()));
        }
    }
}
