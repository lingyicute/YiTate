package com.lingyicute.orientationlock.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import com.lingyicute.orientationlock.utils.OrientationUtils;

public class PreferenceManager {

    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_NOTIFICATION_REQUESTED = "notification_permission_requested";
    private static final String KEY_NOTIFICATION_RATIONALE_SHOWN = "notification_rationale_shown";

    private static PreferenceManager sManager;
    private final SharedPreferences sPreferences;

    private static final String PREFERENCES = "settings";

    public static synchronized PreferenceManager getInstance(Context context) {
        if (sManager == null) {
            sManager = new PreferenceManager(context);
        }
        return sManager;
    }

    private PreferenceManager(Context context) {
        sPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private void putInt(String name, int value) {
        if (name == null) {
            return;
        }
        sPreferences.edit().putInt(name, value).apply();
    }

    /** Whether the POST_NOTIFICATIONS prompt has already been shown at least once. */
    public boolean isNotificationPermissionRequested() {
        try {
            return sPreferences.getBoolean(KEY_NOTIFICATION_REQUESTED, false);
        } catch (ClassCastException e) {
            return false;
        }
    }

    public void setNotificationPermissionRequested(boolean requested) {
        sPreferences.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, requested).apply();
    }

    /** Whether the POST_NOTIFICATIONS rationale dialog has already been shown. */
    public boolean isNotificationRationaleShown() {
        try {
            return sPreferences.getBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, false);
        } catch (ClassCastException e) {
            return false;
        }
    }

    public void setNotificationRationaleShown(boolean shown) {
        sPreferences.edit().putBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, shown).apply();
    }

    public void setOrientation(int orientation) {
        putInt(KEY_ORIENTATION, orientation);
    }

    public int getOrientation() {
        int orientation;
        try {
            orientation = sPreferences.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } catch (ClassCastException e) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
        return OrientationUtils.isSupported(orientation)
                ? orientation : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }
}
