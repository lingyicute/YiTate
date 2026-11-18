package com.lingyicute.orientationlock.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;

public class PermissionUtils {
    private static final int LEGACY_OP_SYSTEM_ALERT_WINDOW = 24;

    public static boolean isDrawOverlaysPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(context);
        }
        // On Lollipop the window policy checks the install-time permission first;
        // the window manager separately uses the app-op to decide visibility.
        if (context.checkPermission(Manifest.permission.SYSTEM_ALERT_WINDOW,
                Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps != null) {
            try {
                // Before API 23 this op has no public string name. The throwing variant
                // turns MODE_ERRORED into a wrapped exception rather than a usable result.
                Method checkOp = AppOpsManager.class.getMethod(
                        "checkOpNoThrow", int.class, int.class, String.class);
                int result = (Integer) checkOp.invoke(appOps, LEGACY_OP_SYSTEM_ALERT_WINDOW,
                        Process.myUid(), context.getPackageName());
                return result == AppOpsManager.MODE_ALLOWED;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Some OEM builds may not expose the hidden check. Use the install-time grant.
            }
        }
        return true; // The install-time grant was checked above.
    }

    public static boolean requestDrawOverlaysPermission(Context context) {
        Uri packageUri = Uri.parse("package:" + context.getPackageName());
        if (Build.VERSION.SDK_INT >= 23) {
            if (startSettingsActivity(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
                    || startSettingsActivity(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
                return true;
            }
        }
        return startSettingsActivity(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
    }

    private static boolean startSettingsActivity(Context context, Intent intent) {
        // Keep Settings on this activity's back stack when it is the caller.
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private PermissionUtils() {
        throw new IllegalStateException();
    }
}
