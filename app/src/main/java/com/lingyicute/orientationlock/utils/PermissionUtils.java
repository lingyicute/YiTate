package com.lingyicute.orientationlock.utils;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;

public class PermissionUtils {

    public static boolean isDrawOverlaysPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(context);
        }
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return true;
            }
            Method checkOp = AppOpsManager.class.getMethod(
                    "checkOp", Integer.TYPE, Integer.TYPE, String.class);
            int result = (Integer) checkOp.invoke(appOps, 24, Process.myUid(), context.getPackageName());
            return result == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return true;
        }
    }

    public static void requestDrawOverlaysPermission(Context context) {
        Uri packageUri = Uri.parse("package:" + context.getPackageName());
        if (Build.VERSION.SDK_INT >= 23) {
            if (startSettingsActivity(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
                    || startSettingsActivity(context, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) {
                return;
            }
        }
        startSettingsActivity(context, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
    }

    private static boolean startSettingsActivity(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
