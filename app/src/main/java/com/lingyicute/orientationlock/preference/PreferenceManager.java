package com.lingyicute.orientationlock.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import com.lingyicute.orientationlock.utils.OrientationUtils;

public class PreferenceManager {

    private static final String KEY_ORIENTATION = "orientation";

    private static PreferenceManager sManager;
    private static SharedPreferences sPreferences;

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