package com.lingyicute.orientationlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.Manifest;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.lingyicute.orientationlock.preference.PreferenceManager;
import com.lingyicute.orientationlock.service.YiTateService;
import com.lingyicute.orientationlock.utils.*;

import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 100;

    private PreferenceManager preferenceManager;
    private int currentOrientation;
    private final SparseIntArray orientationMap = new SparseIntArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        initView();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationPermission();
        }

        preferenceManager = PreferenceManager.getInstance(this);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, R.id.tv_orientation_default);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, R.id.tv_orientation_full_sensor);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.id.tv_orientation_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, R.id.tv_orientation_reverse_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, R.id.tv_orientation_sensor_landscape);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.id.tv_orientation_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, R.id.tv_orientation_reverse_portrait);
        orientationMap.put(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, R.id.tv_orientation_sensor_portrait);

        int orientation = PermissionUtils.isDrawOverlaysPermissionGranted(this)
                ? preferenceManager.getOrientation() : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        setOrientation(orientation);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // 检查是否应该显示权限说明
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("需要通知权限")
                            .setMessage("为了保持屏幕方向锁定服务的正常运行，需要通知权限来显示通知。")
                            .setPositiveButton("授权", (dialog, which) -> {
                                requestPermissions(
                                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                        NOTIFICATION_PERMISSION_REQUEST_CODE);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } else {
                    // 首次请求或用户选择了"不再询问"
                    requestPermissions(
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_PERMISSION_REQUEST_CODE);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限被授予，重启服务以确保通知正常显示
                    if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                        setOrientation(currentOrientation);
                    }
                } else {
                    // 权限被拒绝
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // 用户选择了"不再询问"
                        new AlertDialog.Builder(this)
                                .setTitle("通知权限被禁用")
                                .setMessage("您已禁用通知权限。这可能会影响服务的正常运行，是否要前往设置页面开启权限？")
                                .setPositiveButton("设置", (dialog, which) -> {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    try {
                                        startActivity(intent);
                                    } catch (ActivityNotFoundException e) {
                                        Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        Toast.makeText(this, "通知权限被拒绝，部分功能可能无法正常工作", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private void setOrientation(int orientation) {
        SimpleLog.d(TAG, "select orientation: " + orientation);
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
                PermissionUtils.requestDrawOverlaysPermission(this);
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        preferenceManager.setOrientation(orientation);
        int lastViewId = orientationMap.get(currentOrientation, View.NO_ID);
        if (lastViewId != View.NO_ID) {
            findViewById(lastViewId).setBackgroundResource(R.drawable.bg_button);
        }
        int viewId = orientationMap.get(orientation, View.NO_ID);
        if (viewId != View.NO_ID) {
            findViewById(viewId).setBackgroundResource(R.drawable.bg_selected);
        }
        Intent intent = new Intent(this, YiTateService.class);
        intent.setAction(YiTateService.ACTION_SET_ORIENTATION);
        intent.putExtra(YiTateService.KEY_ORIENTATION, orientation);
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            stopService(intent);
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        currentOrientation = orientation;
    }

    private void initView() {
        LinearLayout rootView = findViewById(R.id.ll_root);
        List<View> childViews = ViewUtils.getAllChildViews(rootView);
        Drawable icon;
        int iconSize = ViewUtils.dp(this, 32);
        int iconColor = Build.VERSION.SDK_INT >= 21
                ? ContextUtils.getColorFromAttr(this, android.R.attr.colorAccent)
                : ContextUtils.getColor(this, R.color.icon_tint);
        for (View view : childViews) {
            if (view instanceof TextView) {
                Drawable[] drawables = ((TextView) view).getCompoundDrawables();
                icon = drawables[1];
                if (icon == null) {
                    continue;
                }
                icon.setBounds(0, 0, iconSize, iconSize);
                ViewUtils.setDrawableColorFilter(icon, iconColor);
                ((TextView) view).setCompoundDrawables(drawables[0], icon, drawables[2], drawables[3]);
                if (view.getId() != View.NO_ID) {
                    view.setOnClickListener(this);
                }
            }
        }
        findViewById(R.id.iv_about).setOnClickListener(v -> {
            Uri uri = Uri.parse("https://github.com/lingyicute/YiTate/blob/main/README.md");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ignore) {
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        int index = orientationMap.indexOfValue(id);
        if (index < 0) {
            return;
        }
        int orientation = orientationMap.keyAt(index);
        setOrientation(orientation);
    }
}