package com.lingyicute.orientationlock;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.utils.ContextUtils;

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
}
