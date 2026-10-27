package com.lingyicute.orientationlock.utils;

import android.content.pm.ActivityInfo;

import com.lingyicute.orientationlock.R;

/** The modes exposed by the UI; reject corrupt preferences and invalid commands. */
public final class OrientationUtils {
    public static boolean isSupported(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return true;
            default:
                return false;
        }
    }

    /** @return the string resource describing {@code orientation}, or 0 if it has no label. */
    public static int getLabelRes(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
                return R.string.orientation_full_sensor;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return R.string.orientation_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                return R.string.orientation_reverse_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return R.string.orientation_sensor_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return R.string.orientation_portrait;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return R.string.orientation_reverse_portrait;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return R.string.orientation_sensor_portrait;
            default:
                return 0;
        }
    }

    private OrientationUtils() { }
}
